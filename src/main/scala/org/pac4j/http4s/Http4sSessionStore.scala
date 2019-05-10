package org.pac4j.http4s

import java.io._
import java.util.Base64
import java.nio.charset.StandardCharsets.UTF_8

import io.circe.{Json, JsonObject}
import org.pac4j.core.context.session.SessionStore
import org.pac4j.http4s.SessionSyntax._
import org.pac4j.http4s.session.Session

trait Http4sSessionStore extends SessionStore[Http4sWebContext] {

  def getSession(context: Http4sWebContext): Option[Session] = context.request.session2

  override def getOrCreateSessionId(context: Http4sWebContext): String = {
    println(s"Http4sSessionStore: getOrCreateSessionId")
    "SessionId"
  }

  override def get(context: Http4sWebContext, key: String): AnyRef = {
    println(s"Http4sSessionStore: get key: $key")
    val result = for {
      session <- getSession(context)
      obj <- session.asObject
      value <- obj(key)
      valueStr <- value.asString
    } yield valueStr
    println(s"Http4sSessionStore: get $key = $result")
    result match {
      case Some(s) => deserialise(s)
      case None => null
    }
  }

  override def set(context: Http4sWebContext, key: String, value: Any): Unit = {
    println(s"Http4sSessionStore: set key: $key value: $value")

    println(s"Http4sSessionStore: Before: ${finalResponseSession(context)}")

    // context needs a modifyResponse( r=> r) function?!
    context.response = context.response.modifySession { s =>
      Json.fromJsonObject(s.asObject.map { jsonObject =>
        val newMap = if (value != null) {
          jsonObject.toMap + (key -> Json.fromString(serialise(value)))
        } else {
          jsonObject.toMap - key
        }
        JsonObject.fromMap(newMap)
      }.getOrElse(JsonObject.empty))
    }

    println(s"Http4sSessionStore: After: ${finalResponseSession(context)}")
  }

  def finalResponseSession(context: Http4sWebContext): Option[Session] = {
    val updateSession = context.response.attributes.get(Session.responseAttr).getOrElse( { os: Option[Session] => os } )
    updateSession(context.request.attributes.get(Session.requestAttr))
  }


  override def destroySession(context: Http4sWebContext): Boolean = {
    println(s"Http4sSessionStore: destroySession")
    context.response = context.response.clearSession
    true
  }

  override def getTrackableSession(context: Http4sWebContext): AnyRef = {
    println(s"Http4sSessionStore: getTrackableSession")
    getSession(context)
  }

  override def buildFromTrackableSession(context: Http4sWebContext, trackableSession: Any): SessionStore[Http4sWebContext] = {
    println(s"Http4sSessionStore: buildFromTrackableSession: ${trackableSession.toString()}")
    new Http4sProvidedSessionStore(getSession(context).orNull)
  }

  override def renewSession(context: Http4sWebContext): Boolean = {
    println(s"!!!!!!!!!!!!! TODO renewSession !!!!!!!!!!!!")
    //context.response = context.response.clearSession
    //context.response = context.response.newSession(getSession(context).getOrElse(Json.fromJsonObject(JsonObject.empty)))
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


object Http4sSessionStore extends Http4sSessionStore


class Http4sProvidedSessionStore(session: Session) extends Http4sSessionStore {
  override def getSession(context: Http4sWebContext): Option[Session] = {
    println("Http4sProvidedSessionStore - getsession")
    Some(session)
  }
}
