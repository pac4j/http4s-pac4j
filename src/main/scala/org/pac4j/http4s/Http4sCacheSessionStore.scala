package org.pac4j.http4s

import java.util.UUID

import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.context.{Cookie, Pac4jConstants}
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
  *
  * @author Iain Cardnell
  */
class Http4sCacheSessionStore extends SessionStore[Http4sWebContext] {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private val cache = scala.collection.mutable.Map[String, Map[String, AnyRef]]()

  override def getOrCreateSessionId(context: Http4sWebContext): String = {
    val id = Option(context.getRequestAttribute(Pac4jConstants.SESSION_ID)) match {
      case Some(sessionId) => sessionId.toString
      case None =>
        context.getRequestCookies.asScala.find(_.getName == Pac4jConstants.SESSION_ID) match {
          case Some(cookie) => cookie.getValue
          case None => createSessionId(context)
        }
    }
    logger.debug(s"getOrCreateSessionId - $id")
    id
  }

  def createSessionId(context: Http4sWebContext): String = {
    val id = UUID.randomUUID().toString
    context.setRequestAttribute(Pac4jConstants.SESSION_ID, id)
    val cookie = new Cookie(Pac4jConstants.SESSION_ID, id)
    cookie.setPath("/")
    context.addResponseCookie(cookie)
    id
  }

  override def get(context: Http4sWebContext, key: String): AnyRef = {
    val sessionId = getOrCreateSessionId(context)
    val sessionMap = cache.getOrElseUpdate(sessionId, Map.empty)
    val value = sessionMap.get(key).orNull
    logger.debug(s"get sessionId: $sessionId key: $key")
    value
  }

  override def set(context: Http4sWebContext, key: String, value: AnyRef): Unit = {
    val sessionId = getOrCreateSessionId(context)
    logger.debug(s"set sessionId: $sessionId key: $key")
    val sessionMap = cache.getOrElseUpdate(sessionId, Map.empty)
    val newMap = if (value == null) {
      sessionMap - key
    } else {
      sessionMap + (key -> value)
    }
    cache.update(sessionId, newMap)
  }

  override def destroySession(context: Http4sWebContext): Boolean = {
    val sessionId = getOrCreateSessionId(context)
    if (cache.contains(sessionId)) {
      cache.remove(sessionId)
      context.setRequestAttribute(Pac4jConstants.SESSION_ID, null)
      context.removeResponseCookie(Pac4jConstants.SESSION_ID)
      true
    } else {
      false
    }
  }

  override def getTrackableSession(context: Http4sWebContext): AnyRef = {
    logger.debug(s"getTrackableSession")
    getOrCreateSessionId(context)
  }

  override def buildFromTrackableSession(context: Http4sWebContext, trackableSession: Any): SessionStore[Http4sWebContext] = {
    context.setRequestAttribute(Pac4jConstants.SESSION_ID, trackableSession.toString)
    this
  }

  override def renewSession(context: Http4sWebContext): Boolean = {
    val oldSessionId = getOrCreateSessionId(context)
    val oldData = cache.get(oldSessionId)

    destroySession(context)

    val newSessionId = createSessionId(context)
    if (oldData.isDefined) {
      cache.update(newSessionId, oldData.get)
    }

    logger.debug(s"Renewed session: $oldSessionId -> $newSessionId")
    true
  }
}
