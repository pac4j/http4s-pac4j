package org.pac4j.http4s

import cats.Monad

import java.util.Date
import cats.data.{Kleisli, OptionT}
import cats.syntax.applicative._
import cats.syntax.eq._
import cats.syntax.functor._
import cats.syntax.option._
import cats.syntax.traverse._
import cats.effect._
import io.circe.jawn

import javax.crypto.spec.SecretKeySpec
import javax.crypto.{Cipher, Mac}
import org.http4s._
import org.slf4j.LoggerFactory

import java.util.Base64
import org.apache.commons.codec.binary.Hex
import mouse.option._
import org.http4s.server.HttpMiddleware
import org.typelevel.vault.Key

import java.nio.charset.StandardCharsets.UTF_8
import scala.concurrent.duration.Duration
import scala.util.Try

/*
 * Cookie based sessions for http4s
 *
 * @author Hugh Giddens
 * @author Iain Cardnell
 */

object SessionSyntax {
  implicit final class RequestOps[F[_]](val v: Request[F]) extends AnyVal {
    def session: Option[Session] = v.attributes.lookup(Session.requestAttr)
  }

  implicit final class ResponseOps[F[_]](val v: Response[F]) extends AnyVal {
    def clearSession: Response[F] =
      v.withAttribute(Session.responseAttr, (_: Option[Session]) => None)

    def modifySession(f: Session => Session):  Response[F] = {
      val lf: Option[Session] => Option[Session] = _.cata(f.andThen(_.some), None)
      v.withAttribute(Session.responseAttr,
        v.attributes.lookup(Session.responseAttr).cata(_.andThen(lf), lf))
    }

    def newOrModifySession(f: Option[Session] => Session): Response[F] = {
      val lf: Option[Session] => Option[Session] = f.andThen(_.some)
        v.withAttribute(Session.responseAttr,
          v.attributes.lookup(Session.responseAttr).cata(_.andThen(lf), lf))
    }

    def newSession(session: Session): Response[F] =
      v.withAttribute(Session.responseAttr, (_: Option[Session]) => Some(session))
  }
}

/**
  * Session Cookie Configuration
  *
  * @param secret 16 bytes secret for cookie security
  */
final case class SessionConfig(
  cookieName: String,
  mkCookie: (String, String) => ResponseCookie,
  secret: List[Byte],
  maxAge: Duration
) {
  private val KeySize = 16
  require(secret.length >= KeySize)
  // Fixme: Two keys should be derived for authentication and ciphering
  private val keyBytes = secret.take(KeySize).toArray

  private val logger = LoggerFactory.getLogger(this.getClass)

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
    new SecretKeySpec(keyBytes, "AES")

  private[this] def encrypt(content: String): String = {
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, keySpec)
    Hex.encodeHexString(cipher.doFinal(content.getBytes(UTF_8)))
  }

  private[this] def decrypt(content: String): Option[String] = {
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.DECRYPT_MODE, keySpec)
    Try(new String(cipher.doFinal(Hex.decodeHex(content)), UTF_8)).toOption
  }

  private[this] def sign(content: String): String = {
    val signMac = Mac.getInstance("HmacSHA1")
    signMac.init(new SecretKeySpec(keyBytes, "HmacSHA1"))
    Base64
      .getEncoder
      .encodeToString(signMac.doFinal(content.getBytes(UTF_8)))
  }

  def cookie[F[_]: Sync](content: String): F[ResponseCookie] =
    Sync[F].delay {
      val now = new Date().getTime / 1000
      val expires = now + maxAge.toSeconds
      val serialized = s"$expires-$content"
      val encrypted = encrypt(serialized)
      val signed = sign(encrypted)
      val cookieValue = s"$signed-$encrypted"
      if (cookieValue.length > Session.cookieWarnLimit)
        logger.warn("Cookie size is too big and might be discarded by client browser. Actual size: {}", cookieValue.length)
      mkCookie(cookieName, cookieValue)
    }

  def check[F[_]: Sync](cookie: RequestCookie): F[Option[String]] =
    Sync[F].delay {
      val now = new Date().getTime / 1000
      cookie.content.split('-') match {
        case Array(signature, encrypted) if constantTimeEquals(signature, sign(encrypted)) =>
          for {
            decrypted <- decrypt(encrypted)
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

  val cookieWarnLimit = 4000

  val requestAttr: Key[Session] = Key.newKey[SyncIO, Session].unsafeRunSync()
  val responseAttr: Key[Option[Session] => Option[Session]] =
    Key.newKey[SyncIO, Option[Session] => Option[Session]].unsafeRunSync()

  private[this] def sessionAsCookie[F[_]: Sync](config: SessionConfig, session: Session): F[ResponseCookie] =
    config.cookie[F](session.noSpaces)

  private[this] def checkSignature[F[_]: Sync](
    config: SessionConfig,
    cookie: RequestCookie
  ): F[Option[Session]] =
    OptionT(config.check(cookie)).mapFilter(jawn.parse(_).toOption).value

  private[this] def sessionFromRequest[F[_]: Sync](
      config: SessionConfig,
      request: Request[F]
    ): F[Option[Session]] =
    (for {
      sessionCookie <- OptionT.fromOption[F](request.cookies.find(_.name === config.cookieName))
      session <- OptionT(checkSignature(config, sessionCookie))
    } yield session).value

  private[this] def debug[F[_]: Sync](msg: String): F[Unit] =
    Sync[F].delay(logger.debug(msg))

  def applySessionUpdates[F[_]: Sync](
    config: SessionConfig,
    sessionFromRequest: Option[Session],
    response: Response[F]): F[Response[F]] = {
      val updateSession = response.attributes.lookup(responseAttr)
        .getOrElse[Option[Session] => Option[Session]](identity)
      updateSession(sessionFromRequest).traverse(sessionAsCookie(config, _))
        .map(_.cata(
          response.addCookie,
          if (sessionFromRequest.isDefined) response.removeCookie(config.cookieName)
          else response
          )
        )
  }

  def sessionManagement[F[_]: Sync](config: SessionConfig): HttpMiddleware[F] =
    service => Kleisli { (req: Request[F]) =>
      for {
        _ <- OptionT.liftF(debug(s"starting for ${req.method} ${req.uri}"))
        sessionFromRequest <- OptionT.liftF(sessionFromRequest(config, req))
        requestWithSession = sessionFromRequest.cata(
          req.withAttribute(requestAttr, _),
          req
        )
        _ <- OptionT.liftF(printRequestSessionKeys(sessionFromRequest))
        response <- service(requestWithSession)
        responseWithSession <- OptionT.liftF(
          applySessionUpdates(config, sessionFromRequest, response)
        )
        _ <- OptionT.liftF(debug(s"finishing for ${req.method} ${req.uri}"))
      } yield responseWithSession
    }

  def sessionRequired[F[_] : Monad](fallback: F[Response[F]]): HttpMiddleware[F] =
    service => Kleisli { (req: Request[F]) =>
      import SessionSyntax._
      OptionT(req.session.pure[F])
        .flatMap(_ => service(req))
        .orElse(OptionT.liftF(fallback))
    }

  private[this] def printRequestSessionKeys[F[_]: Sync](sessionOpt: Option[Session]) =
     sessionOpt match {
        case Some(session) => debug("Request Session contains keys: " + session.asObject.map(_.toMap.keys.mkString(", ")))
        case None => debug("Request Session empty")
    }
}
