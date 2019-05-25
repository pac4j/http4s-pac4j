package org.pac4j.http4s

import java.util.Date

import io.circe.jawn
import javax.crypto.spec.SecretKeySpec
import javax.crypto.{Cipher, Mac}
import javax.xml.bind.DatatypeConverter
import org.http4s.Http4s._
import org.http4s._
import org.http4s.headers.{Cookie => CookieHeader}
import org.http4s.server.{HttpMiddleware, Middleware}
import org.pac4j.http4s.SessionSyntax._
import org.slf4j.LoggerFactory
import scalaz.OptionT
import scalaz.Scalaz._
import scalaz.concurrent.Task

import scala.concurrent.duration.Duration
import scala.util.Try

/*
 * Cookie based sessions for http4s
 *
 * @author Hugh Giddens
 * @author Iain Cardnell
 */



object SessionSyntax {
  implicit final class RequestOps(val v: Request) extends AnyVal {
    def session: Task[Option[Session]] =
      Task.now(v.attributes.get(Session.requestAttr))

    def session2: Option[Session] =
      v.attributes.get(Session.requestAttr)
  }

  implicit final class TaskResponseOps(val v: Task[Response]) extends AnyVal {
    def clearSession: Task[Response] =
      v.withAttribute(Session.responseAttr(_ => None))

    def modifySession(f: Session => Session): Task[Response] = {
      val lf: Option[Session] => Option[Session] = _.cata(f.andThen(_.some), None)
      v.map { response =>
        response.withAttribute(Session.responseAttr(response.attributes.get(Session.responseAttr).cata(_.andThen(lf), lf)))
      }
    }

    def newSession(session: Session): Task[Response] =
      v.withAttribute(Session.responseAttr(_ => Some(session)))
  }

  implicit final class ResponseOps(val v: Response) extends AnyVal {
    def clearSession: Response =
      v.withAttribute(Session.responseAttr(_ => None))

    def modifySession(f: Session => Session): Response = {
      val lf: Option[Session] => Option[Session] = _.cata(f.andThen(_.some), None)
      v.withAttribute(Session.responseAttr(v.attributes.get(Session.responseAttr).cata(_.andThen(lf), lf)))
    }

    def newOrModifySession(f: Option[Session] => Session): Response = {
      val newUpdateFn: Option[Session] => Option[Session] = v.attributes.get(Session.responseAttr) match {
        case Some(currentUpdateFn) => currentUpdateFn.andThen(f.andThen(_.some))
        case None => f.andThen(_.some)
      }
      v.withAttribute(Session.responseAttr(newUpdateFn))
    }

    def newSession(session: Session): Response =
      v.withAttribute(Session.responseAttr(_ => Some(session)))
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
  mkCookie: (String, String) => Cookie,
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
    DatatypeConverter.printHexBinary(cipher.doFinal(content.getBytes("UTF-8")))
  }

  private[this] def decrypt(content: String): Option[String] = {
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.DECRYPT_MODE, keySpec)
    Try(new String(cipher.doFinal(DatatypeConverter.parseHexBinary(content)), "UTF-8")).toOption
  }

  private[this] def sign(content: String): String = {
    val signKey = secret.getBytes("UTF-8")
    val signMac = Mac.getInstance("HmacSHA1")
    signMac.init(new SecretKeySpec(signKey, "HmacSHA256"))
    DatatypeConverter.printBase64Binary(signMac.doFinal(content.getBytes("UTF-8")))
  }

  def cookie(content: String): Task[Cookie] =
    Task.delay {
      val now = new Date().getTime / 1000
      val expires = now + maxAge.toSeconds
      val serialized = s"$expires-$content"
      val signed = sign(serialized)
      val encrypted = encrypt(serialized)
      mkCookie(cookieName, s"$signed-$encrypted")
    }

  def cookie2(content: String): Cookie = {
      val now = new Date().getTime / 1000
      val expires = now + maxAge.toSeconds
      val serialized = s"$expires-$content"
      val signed = sign(serialized)
      val encrypted = encrypt(serialized)
      mkCookie(cookieName, s"$signed-$encrypted")
    }

  def check(cookie: Cookie): Task[Option[String]] =
    Task.delay {
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

  val requestAttr = AttributeKey[Session]
  val responseAttr = AttributeKey[Option[Session] => Option[Session]]

  private[this] def sessionAsCookie(config: SessionConfig, session: Session): Task[Cookie] =
    config.cookie(session.noSpaces)

  private[this] def sessionAsCookie2(config: SessionConfig, session: Session): Cookie =
    config.cookie2(session.noSpaces)

  private[this] def checkSignature(config: SessionConfig, cookie: Cookie): Task[Option[Session]] =
    config.check(cookie).map(_.flatMap(jawn.parse(_).toOption))

  private[this] def sessionFromRequest(config: SessionConfig, request: Request): Task[Option[Session]] =
    (for {
      allCookies <- OptionT(Task.now(CookieHeader.from(request.headers)))
      sessionCookie <- OptionT(Task.now(allCookies.values.list.find(_.name === config.cookieName)))
      session <- OptionT(checkSignature(config, sessionCookie))
    } yield session).run

  private[this] def applySessionUpdates(
    config: SessionConfig,
    sessionFromRequest: Option[Session],
    serviceResponse: MaybeResponse): Task[MaybeResponse] = {
    Task.delay {
      serviceResponse match {
        case response: Response =>
          val updateSession = response.attributes.get(responseAttr) | identity
          updateSession(sessionFromRequest).cata(
            session => {
              response.addCookie(sessionAsCookie2(config, session))
            },
            if (sessionFromRequest.isDefined) response.removeCookie(config.cookieName) else response
          )
        case Pass => Pass
      }
    }
  }

  def sessionManagement(config: SessionConfig): HttpMiddleware =
    Middleware { (request, service) =>
      logger.debug(s"starting for ${request.method} ${request.uri}")
      for {
        sessionFromRequest <- sessionFromRequest(config, request)
        requestWithSession = sessionFromRequest.cata(
          session => request.withAttribute(requestAttr, session),
          request
        )
        _ <- printRequestSessionKeys(requestWithSession.session2)
        maybeResponse <- service(requestWithSession)
        responseWithSession <- applySessionUpdates(config, sessionFromRequest, maybeResponse)
        _ <- Task.delay { logger.debug(s"finishing for ${request.method} ${request.uri}") }
      } yield responseWithSession
    }

  def sessionRequired(fallback: Task[Response]): HttpMiddleware =
    Middleware { (request, service) =>
      import SessionSyntax._
      OptionT(request.session).flatMapF(_ => service(request)).getOrElseF(fallback)
    }

  def printRequestSessionKeys(sessionOpt: Option[Session]): Task[Unit] =
    Task.delay {
      sessionOpt match {
        case Some(session) => logger.debug("Request Session contains keys: " + session.asObject.map(_.keys.mkString(", ")))
        case None => logger.debug("Request Session empty")
      }
    }
}
