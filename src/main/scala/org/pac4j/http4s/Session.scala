package org.pac4j.http4s

import java.util.Date

import cats.implicits._
import cats.data.OptionT
import cats.effect._
import io.chrisdavenport.vault.Key
import io.circe.jawn
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{Cipher, Mac}
import org.http4s.Http4s._
import org.http4s._
import org.http4s.server.{HttpMiddleware, Middleware}
import org.pac4j.http4s.SessionSyntax._
import org.slf4j.LoggerFactory
import java.util.Base64
import org.apache.commons.codec.binary.Hex
import mouse.option._

import scala.concurrent.duration.Duration
import scala.util.Try

/*
 * Cookie based sessions for http4s
 *
 * @author Hugh Giddens
 * @author Iain Cardnell
 */

object SessionSyntax {
  implicit final class RequestOps(val v: Request[IO]) extends AnyVal {
    def session: Option[Session] = v.attributes.lookup(Session.requestAttr)
  }

  implicit final class ResponseOps(val v: Response[IO]) extends AnyVal {
    def clearSession: Response[IO] =
      v.withAttribute(Session.responseAttr, (_: Option[Session]) => None)

    def modifySession(f: Session => Session):  Response[IO] = {
      val lf: Option[Session] => Option[Session] = _.cata(f.andThen(_.some), None)
      v.withAttribute(Session.responseAttr,
        v.attributes.lookup(Session.responseAttr).cata(_.andThen(lf), lf))
    }

    def newOrModifySession(f: Option[Session] => Session): Response[IO] = {
      val lf: Option[Session] => Option[Session] = f.andThen(_.some)
        v.withAttribute(Session.responseAttr,
          v.attributes.lookup(Session.responseAttr).cata(_.andThen(lf), lf))
    }

    def newSession(session: Session): Response[IO] =
      v.withAttribute(Session.responseAttr, (_: Option[Session]) => Some(session))
  }
}

/**
  * Session Cookie Configuration
  *
  * @param cookieName
  * @param mkCookie
  * @param secret
  * @param maxAge
  */
final case class SessionConfig(
  cookieName: String,
  mkCookie: (String, String) => ResponseCookie,
  secret: String,
  maxAge: Duration
) {
  require(secret.length >= 16)

  def constantTimeEquals(a: String, b: String): Boolean =
    if (a.length != b.length) {
      false
    } else {
      var equal = 0
      for (i <- Array.range(0, a.length)) {
        equal |= a(i) ^ b(i)
      }
      equal == 0
    }

  private[this] def keySpec: SecretKeySpec =
    new SecretKeySpec(secret.substring(0, 16).getBytes("UTF-8"), "AES")

  private[this] def encrypt(content: String): String = {
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec)
    Hex.encodeHexString(cipher.doFinal(content.getBytes("UTF-8")))
  }

  private[this] def decrypt(content: String): Option[String] = {
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.DECRYPT_MODE, keySpec)
    Try(new String(cipher.doFinal(Hex.decodeHex(content)), "UTF-8")).toOption
  }

  private[this] def sign(content: String): String = {
    val signKey = secret.getBytes("UTF-8")
    val signMac = Mac.getInstance("HmacSHA1")
    signMac.init(new SecretKeySpec(signKey, "HmacSHA256"))
    Base64
      .getEncoder
      .encodeToString(signMac.doFinal(content.getBytes("UTF-8")))
  }

  def cookie[F[_]: Sync](content: String): F[ResponseCookie] =
    Sync[F].delay {
      val now = new Date().getTime / 1000
      val expires = now + maxAge.toSeconds
      val serialized = s"$expires-$content"
      val signed = sign(serialized)
      val encrypted = encrypt(serialized)
      mkCookie(cookieName, s"$signed-$encrypted")
    }

  def check(cookie: RequestCookie): IO[Option[String]] =
    IO.delay {
      val now = new Date().getTime / 1000
      cookie.content.split('-') match {
        case Array(signature, value) =>
          for {
            decrypted <- decrypt(value) if constantTimeEquals(signature, sign(decrypted))
            Array(expires, content) = decrypted.split("-", 2)
            expiresSeconds <- Try(expires.toLong).toOption if expiresSeconds > now
          } yield content
        case _ =>
          None
      }
    }
}

object Session {
  private val logger = LoggerFactory.getLogger(this.getClass)

  val requestAttr: Key[Session] = Key.newKey[SyncIO, Session].unsafeRunSync
  val responseAttr: Key[Option[Session] => Option[Session]] =
    Key.newKey[SyncIO, Option[Session] => Option[Session]].unsafeRunSync

  private[this] def sessionAsCookie(config: SessionConfig, session: Session): IO[ResponseCookie] =
    config.cookie[IO](session.noSpaces)

  private[this] def checkSignature(
    config: SessionConfig,
    cookie: RequestCookie
  ): IO[Option[Session]] =
    OptionT(config.check(cookie)).mapFilter(jawn.parse(_).toOption).value

  private[this] def sessionFromRequest(
      config: SessionConfig,
      request: Request[IO]
    ): IO[Option[Session]] =
    (for {
      sessionCookie <- OptionT.fromOption[IO](request.cookies.find(_.name === config.cookieName))
      session <- OptionT(checkSignature(config, sessionCookie))
    } yield session).value

  private[this] def debug(msg: String): IO[Unit] =
    IO.delay(logger.debug(msg))

  def applySessionUpdates(
    config: SessionConfig,
    sessionFromRequest: Option[Session],
    response: Response[IO]): IO[Response[IO]] = {
      val updateSession = response.attributes.lookup(responseAttr)
        .getOrElse[Option[Session] => Option[Session]](identity _)
      updateSession(sessionFromRequest).traverse(sessionAsCookie(config, _))
        .map(_.cata(
          response.addCookie(_),
          if (sessionFromRequest.isDefined) response.removeCookie(config.cookieName)
          else response
          )
        )
  }

  def sessionManagement(config: SessionConfig): HttpMiddleware[IO] =
    Middleware { (request, service) =>
      for {
        _ <- OptionT.liftF(debug(s"starting for ${request.method} ${request.uri}"))
        sessionFromRequest <- OptionT.liftF(sessionFromRequest(config, request))
        requestWithSession = sessionFromRequest.cata(
            request.withAttribute(requestAttr, _),
            request
          )
        _ <- OptionT.liftF(printRequestSessionKeys(sessionFromRequest))
        response <- service(requestWithSession)
        responseWithSession <- OptionT.liftF(
          applySessionUpdates(config, sessionFromRequest, response)
        )
        _ <- OptionT.liftF(debug(s"finishing for ${request.method} ${request.uri}"))
      } yield responseWithSession
    }

  def sessionRequired(fallback: IO[Response[IO]]): HttpMiddleware[IO] =
    Middleware { (request, service) =>
      import SessionSyntax._
      OptionT(request.session.pure[IO])
        .flatMap(_ => service(request))
        .orElse(OptionT.liftF(fallback))
    }

  private[this] def printRequestSessionKeys(sessionOpt: Option[Session]) =
     sessionOpt match {
        case Some(session) => debug("Request Session contains keys: " + session.asObject.map(_.toMap.keys.mkString(", ")))
        case None => debug("Request Session empty")
    }
}
