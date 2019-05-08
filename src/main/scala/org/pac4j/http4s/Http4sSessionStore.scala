package org.pac4j.http4s

import java.io._
import java.util.Base64
import java.nio.charset.StandardCharsets.UTF_8

import io.circe.{Json, JsonObject}
import org.pac4j.core.context.session.SessionStore
import org.pac4j.http4s.Syntax._

object Http4sSessionStore extends SessionStore[Http4sWebContext] {
  override def getOrCreateSessionId(context: Http4sWebContext): String = ???

  override def get(context: Http4sWebContext, key: String): AnyRef = {
    println(s"Http4sSessionStore: get key: $key")
    val result = for {
      session <- context.request.session2
      obj <- session.asObject
      value <- obj(key)
      valueStr <- value.asString
    } yield valueStr
    result match {
      case Some(s) => deserialise(s)
      case None => null
    }
  }

  override def set(context: Http4sWebContext, key: String, value: Any): Unit = {
    println(s"Http4sSessionStore: set key: $key value: $value")

    // context needs a modifyResponse( r=> r) function?!
    context.response = context.response.modifySession { s =>
      Json.fromJsonObject(s.asObject.map { jsonObject =>
        JsonObject.fromMap(jsonObject.toMap + (key -> Json.fromString(serialise(value))))
        }.getOrElse(JsonObject.empty))
    }
  }

  override def destroySession(context: Http4sWebContext): Boolean = ???

  override def getTrackableSession(context: Http4sWebContext): AnyRef = ???

  override def buildFromTrackableSession(context: Http4sWebContext, trackableSession: Any): SessionStore[Http4sWebContext] = ???

  override def renewSession(context: Http4sWebContext): Boolean = ???

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
