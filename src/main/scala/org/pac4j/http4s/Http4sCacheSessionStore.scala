package org.pac4j.http4s

import cats.effect.Sync

import java.util.{Optional, UUID}
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.context.{Cookie, WebContext}
import org.pac4j.core.util.Pac4jConstants
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._

/**
  * Http4sCacheSessionStore is an in memory session implementation.
  *
  * The cookie will just contain an id and the session data is kept server side
  * in `cache`.
  *
  * Note that as `cache` is a simple Map, if multiple web servers are running
  * sticky sessions will be required for this to work.
  * @param maxAge `Max-Age` cookie attribute
  * @param expires `Expires` cookie attribute
  * @param domain `Domain` cookie attribute
  * @param path `Path` cookie attribute
  * @param secure `Secure` cookie attribute
  * @param httpOnly `HttpOnly` cookie attribute
  *
  * @author Iain Cardnell
  */
class Http4sCacheSessionStore[F[_] : Sync](
  maxAge: Option[Int] = None,
  domain: Option[String] = None,
  path: Option[String] = Some("/"),
  secure: Boolean = false,
  httpOnly: Boolean = false
) extends SessionStore {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private val cache = scala.collection.mutable.Map[String, Map[String, AnyRef]]()

  override def getSessionId(context: WebContext, createSession: Boolean): Optional[String] = {
    val id = Option(context.getRequestAttribute(Pac4jConstants.SESSION_ID).asInstanceOf[Optional[AnyRef]].orElse(null)) match {
      case Some(sessionId) => sessionId.toString
      case None =>
        context.getRequestCookies.asScala.find(_.getName == Pac4jConstants.SESSION_ID) match {
          case Some(cookie) => cookie.getValue
          case None => createSessionId(context.asInstanceOf[Http4sWebContext[F]])
        }
    }
    logger.debug(s"getOrCreateSessionId - $id")
    Optional.ofNullable(id)
  }

  private def createSessionId(context: Http4sWebContext[F]): String = {
    val id = UUID.randomUUID().toString
    context.setRequestAttribute(Pac4jConstants.SESSION_ID, id)

    val cookie = new Cookie(Pac4jConstants.SESSION_ID, id)
    maxAge.foreach(cookie.setMaxAge)
    domain.foreach(cookie.setDomain)
    path.foreach(cookie.setPath)
    cookie.setSecure(secure)
    cookie.setHttpOnly(httpOnly)

    context.addResponseCookie(cookie)
    id
  }

  override def get(context: WebContext, key: String): Optional[AnyRef] = {
    val sessionId = getSessionId(context, false)
    sessionId.map[AnyRef](sid => {
        val sessionMap = cache.getOrElseUpdate(sid, Map.empty)
        val value = sessionMap.get(key).orNull
        logger.debug(s"get sessionId: $sessionId key: $key")
        value
      }
    )
  }

  override def set(context: WebContext, key: String, value: AnyRef): Unit = {
    val sessionId = getSessionId(context, true).get()
    logger.debug(s"set sessionId: $sessionId key: $key")
    val sessionMap = cache.getOrElseUpdate(sessionId, Map.empty)
    val newMap = if (value == null) {
      sessionMap - key
    } else {
      sessionMap + (key -> value)
    }
    cache.update(sessionId, newMap)
  }

  override def destroySession(context: WebContext): Boolean = {
    val sessionId = getSessionId(context, false)
    if (sessionId.isPresent && cache.contains(sessionId.get())) {
      cache.remove(sessionId.get())
      context.setRequestAttribute(Pac4jConstants.SESSION_ID, null)
      context.asInstanceOf[Http4sWebContext[F]].removeResponseCookie(Pac4jConstants.SESSION_ID)
      true
    } else {
      false
    }
  }

  override def getTrackableSession(context: WebContext): Optional[AnyRef] = {
    logger.debug(s"getTrackableSession")
    getSessionId(context, false).asInstanceOf[Optional[AnyRef]]
  }

  override def buildFromTrackableSession(context: WebContext, trackableSession: Any): Optional[SessionStore] = {
    context.setRequestAttribute(Pac4jConstants.SESSION_ID, trackableSession.toString)
    Optional.of(this)
  }

  override def renewSession(context: WebContext): Boolean = {
    val oldSessionId = getSessionId(context, false)
    val oldData = oldSessionId.flatMap(sid => Optional.ofNullable(cache.get(sid).orNull))

    destroySession(context)

    val newSessionId = createSessionId(context.asInstanceOf[Http4sWebContext[F]])
    if (oldData.isPresent) {
      cache.update(newSessionId, oldData.get)
    }

    logger.debug(s"Renewed session: $oldSessionId -> $newSessionId")
    true
  }
}
