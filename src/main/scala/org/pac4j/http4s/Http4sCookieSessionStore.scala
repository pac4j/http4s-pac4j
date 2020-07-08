package org.pac4j.http4s

import java.io._
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

import io.circe.{Json, JsonObject}
import org.pac4j.core.context.session.SessionStore
import org.pac4j.http4s.SessionSyntax._
import org.slf4j.LoggerFactory


/**
  * Http4sCookieSessionStore is session implementation based on cookies.
  *
  * All session data is kept in the client cookie (encrypted with the key
  * specified in SessionConfig).
  *
  * @author Iain Cardnell
  */
trait Http4sCookieSessionStore extends SessionStore[Http4sWebContext] {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def getSession(context: Http4sWebContext): Option[Session] =
    context.getRequest.session

  override def getOrCreateSessionId(context: Http4sWebContext): String = {
    "pac4j"
  }

  override def get(context: Http4sWebContext, key: String): AnyRef = {
    logger.debug(s"get key: $key ")
    getOpt(context, key) match {
      case Some(s) => s
      case None => null
    }
  }

  def getOpt(context: Http4sWebContext, key: String): Option[AnyRef] = {
    get(getSession(context), key)
  }

  def get(sessionOpt: Option[Session], key: String): Option[AnyRef] = {
    for {
      session <- sessionOpt
      obj <- session.asObject
      value <- obj(key)
      valueStr <- value.asString
    } yield deserialise(valueStr)
  }

  override def set(context: Http4sWebContext, key: String, value: Any): Unit = {
    logger.debug(s"set key: $key")

    context.modifyResponse { r =>
      r.newOrModifySession { f => set(f, key, value) }
    }
  }

  def set(sessionOpt: Option[Session], key: String, value: Any): Session = {
    sessionOpt match {
      case Some(s) =>
        Json.fromJsonObject(s.asObject.map { jsonObject =>
          val newMap = if (value != null) {
            jsonObject.toMap + (key -> Json.fromString(serialise(value)))
          } else {
            jsonObject.toMap - key
          }
          JsonObject.fromMap(newMap)
        }.getOrElse(JsonObject.empty))
      case None =>
        Json.fromJsonObject(JsonObject.singleton(key, Json.fromString(serialise(value))))
    }
  }

  override def destroySession(context: Http4sWebContext): Boolean = {
    logger.debug("destroySession")
    context.modifyResponse(r => r.clearSession)
    true
  }

  override def getTrackableSession(context: Http4sWebContext): AnyRef = {
    logger.debug("getTrackableSession")
    getSession(context)
  }

  override def buildFromTrackableSession(context: Http4sWebContext, trackableSession: Any): SessionStore[Http4sWebContext] = {
    // Everything stored in cookie
    this
  }

  override def renewSession(context: Http4sWebContext): Boolean = {
    // Everything stored in cookie
    true
  }

  def serialise(value: Any): String = {
    val stream: ByteArrayOutputStream = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(stream)
    oos.writeObject(value)
    oos.close()
    new String(
      Base64.getEncoder.encode(stream.toByteArray),
      UTF_8
    )
  }

  def deserialise(str: String): AnyRef = {
    val bytes = Base64.getDecoder.decode(str.getBytes(UTF_8))
    val ois = new ObjectInputStream(new ByteArrayInputStream(bytes))
    val value = ois.readObject
    ois.close()
    value
  }
}

object Http4sCookieSessionStore extends Http4sCookieSessionStore
