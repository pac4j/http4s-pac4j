package org.pac4j.http4s

import cats.effect.IO

import java.io._
import java.nio.charset.StandardCharsets.UTF_8
import java.util.{Base64, Optional}
import io.circe.{Json, JsonObject}
import org.pac4j.core.context.WebContext
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
trait Http4sCookieSessionStore[F[_]] extends SessionStore {
  private val logger = LoggerFactory.getLogger(this.getClass)

  override def getSessionId(context: WebContext, createSession: Boolean): Optional[String] =
    Optional.of("pac4j")

  private def getSession(context: WebContext): Option[Session] =
    context.asInstanceOf[Http4sWebContext[F]].getRequest.session

  override def get(context: WebContext, key: String): Optional[AnyRef] = {
    logger.debug(s"get key: $key ")
    Optional.ofNullable(getOpt(context, key).orNull)
  }

  private def getOpt(context: WebContext, key: String): Option[AnyRef] = {
    get(getSession(context), key)
  }

  /* private */ def get(sessionOpt: Option[Session], key: String): Option[AnyRef] = {
    for {
      session <- sessionOpt
      obj <- session.asObject
      value <- obj(key)
      valueStr <- value.asString
    } yield deserialise(valueStr)
  }

  override def set(context: WebContext, key: String, value: Any): Unit = {
    logger.debug(s"set key: $key")

    context.asInstanceOf[Http4sWebContext[F]].modifyResponse { r =>
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

  override def destroySession(context: WebContext): Boolean = {
    logger.debug("destroySession")
    context.asInstanceOf[Http4sWebContext[F]].modifyResponse(r => r.clearSession)
    true
  }

  override def getTrackableSession(context: WebContext): Optional[AnyRef] = {
    logger.debug("getTrackableSession")
    Optional.ofNullable(getSession(context).orNull)
  }

  override def buildFromTrackableSession(context: WebContext, trackableSession: Any): Optional[SessionStore] = {
    // Everything stored in cookie
    Optional.of(this)
  }

  override def renewSession(context: WebContext): Boolean = {
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

object Http4sCookieSessionStore {
  def ioInstance = new Http4sCookieSessionStore[IO]{}
}
