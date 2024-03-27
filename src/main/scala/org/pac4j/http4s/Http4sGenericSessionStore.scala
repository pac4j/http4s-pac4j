package org.pac4j.http4s

import cats.effect.Sync
import cats.effect.std.Dispatcher
import org.http4s.SameSite
import org.pac4j.core.context.{Cookie, WebContext}
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.util.Pac4jConstants
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import cats.effect.std.UUIDGen
import java.util.Optional

/** Http4sGenericSessionStore is a generic session implementation with
  * configurable storage
  *
  * The cookie will just contain an id and the session data is kept server side
  * in `storage`.
  *
  * @param maxAge
  *   `Max-Age` cookie attribute
  * @param domain
  *   `Domain` cookie attribute
  * @param path
  *   `Path` cookie attribute
  * @param secure
  *   `Secure` cookie attribute
  * @param httpOnly
  *   `HttpOnly` cookie attribute
  *
  * @author
  *   Iain Cardnell
  */
class Http4sGenericSessionStore[F[_]: Sync](
    sessionRepository: SessionRepository[F],
    dispatcher: Dispatcher[F]
)(
    maxAge: Option[Int] = None,
    domain: Option[String] = None,
    path: Option[String] = Some("/"),
    secure: Boolean = false,
    httpOnly: Boolean = false,
    sameSite: Option[SameSite] = None
) extends SessionStore {
  private val logger = LoggerFactory.getLogger(this.getClass)

  override def getSessionId(
      context: WebContext,
      createSession: Boolean
  ): Optional[String] = {
    val id =
      context.getRequestAttribute(Pac4jConstants.SESSION_ID).toScala match {
        case Some(sessionId) => Some(sessionId.toString)
        case None =>
          context.getRequestCookies.asScala.find(
            _.getName == Pac4jConstants.SESSION_ID
          ) match {
            case Some(cookie) => Option(cookie.getValue)
            case None if createSession =>
              Some(createSessionId(context.asInstanceOf[Http4sWebContext[F]]))
            case None => None
          }
      }
    logger.debug(s"getOrCreateSessionId - $id")
    id.toJava
  }

  private def createSessionId(context: Http4sWebContext[F]): String = {
    val id = dispatcher.unsafeRunSync(UUIDGen[F].randomUUID).toString()
    context.setRequestAttribute(Pac4jConstants.SESSION_ID, id)

    val cookie = new Cookie(Pac4jConstants.SESSION_ID, id)
    maxAge.foreach(cookie.setMaxAge)
    domain.foreach(cookie.setDomain)
    path.foreach(cookie.setPath)
    cookie.setSecure(secure)
    cookie.setHttpOnly(httpOnly)
    sameSite.foreach(s => cookie.setSameSitePolicy(s.renderString))

    context.addResponseCookie(cookie)
    id
  }

  override def get(context: WebContext, key: String): Optional[AnyRef] = {
    val sessionId = getSessionId(context, createSession = false)
    sessionId.flatMap[AnyRef] { sid =>
      val value = dispatcher
        .unsafeRunSync(sessionRepository.get(sid))
        .getOrElse(Map.empty)
        .get(key)
        .toJava
      logger.debug(s"get sessionId: $sessionId key: $key")
      value
    }
  }

  override def set(context: WebContext, key: String, value: AnyRef): Unit = {
    val sessionId = getSessionId(context, createSession = true).get()
    if (value == null) {
      dispatcher.unsafeRunSync(sessionRepository.remove(sessionId, key))
    } else
      dispatcher.unsafeRunSync(sessionRepository.set(sessionId, key, value))
  }

  override def destroySession(context: WebContext): Boolean = {
    val sessionId = getSessionId(context, createSession = false).toScala
    val deleted = sessionId.exists(id =>
      dispatcher.unsafeRunSync(sessionRepository.deleteSession(id))
    )
    if (deleted) {
      context.setRequestAttribute(Pac4jConstants.SESSION_ID, null)
      context
        .asInstanceOf[Http4sWebContext[F]]
        .removeResponseCookie(Pac4jConstants.SESSION_ID)
    }
    deleted
  }

  override def getTrackableSession(context: WebContext): Optional[AnyRef] = {
    logger.debug(s"getTrackableSession")
    getSessionId(context, false).asInstanceOf[Optional[AnyRef]]
  }

  override def buildFromTrackableSession(
      context: WebContext,
      trackableSession: Any
  ): Optional[SessionStore] = {
    context.setRequestAttribute(
      Pac4jConstants.SESSION_ID,
      trackableSession.toString
    )
    Optional.of(this)
  }

  override def renewSession(context: WebContext): Boolean = {
    val oldSessionId = getSessionId(context, false)
    val oldData = oldSessionId.flatMap(sid =>
      dispatcher.unsafeRunSync(sessionRepository.get(sid)).toJava
    )

    destroySession(context)

    val newSessionId = createSessionId(
      context.asInstanceOf[Http4sWebContext[F]]
    )
    if (oldData.isPresent) {
      dispatcher.unsafeRunSync(
        sessionRepository.update(newSessionId, oldData.get)
      )
    }
    logger.debug(s"Renewed session: $oldSessionId -> $newSessionId")
    true
  }
}
