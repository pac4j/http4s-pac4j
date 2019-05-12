package org.pac4j.http4s

import java.util

import org.http4s.{AttributeKey, Charset, Header, HttpDate, MediaType, Request, Response, Status, UrlForm}
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.context.{Cookie, WebContext}
import org.http4s.headers.`Content-Type`
import org.http4s.headers.{Cookie => CookieHeader}
import org.http4s.util.NonEmptyList
import org.pac4j.core.profile.CommonProfile
import scalaz.{-\/, \/-}

import scala.collection.JavaConverters._

class Http4sWebContext(private var request: Request, private var response: Response) extends WebContext {
  case class Pac4jUserProfiles(pac4jUserProfiles: util.LinkedHashMap[String, CommonProfile])

  val pac4jUserProfilesAttr: AttributeKey[Pac4jUserProfiles] = AttributeKey[Pac4jUserProfiles]

  override def getSessionStore: SessionStore[Http4sWebContext] = Http4sSessionStore

  override def getRequestParameter(name: String): String = {
    if (request.contentType.contains(`Content-Type`(MediaType.`application/x-www-form-urlencoded`))) {
      println(s"getRequestParameter: Getting from Url Encoded Form name=$name")
      UrlForm.decodeString(Charset.`UTF-8`)(getRequestContent) match {
        case -\/(err) => throw new Exception(err.toString)
        case \/-(urlForm) => urlForm.getFirstOrElse(name, request.params.get(name).orNull)
      }
    } else {
      println(s"getRequestParameter: Getting from query params name=$name")
      request.params.get(name).orNull
    }
  }

  override def getRequestParameters: util.Map[String, Array[String]] = {
    println(s"getRequestParameters")
    request.params.toSeq.map(a => (a._1, Array(a._2))).toMap.asJava
  }

  override def getRequestAttribute(name: String): AnyRef = {
    println(s"getRequestAttribute: $name")
    name match {
      case "pac4jUserProfiles" =>
        request.attributes.get(pac4jUserProfilesAttr).orNull
      case _ =>
        throw new NotImplementedError(s"getRequestAttribute for $name not implemented")
    }
  }

  override def setRequestAttribute(name: String, value: Any): Unit = {
    println(s"setRequestAttribute: $name = ${value.toString}")
    request = name match {
      case "pac4jUserProfiles" =>
        request.withAttribute(pac4jUserProfilesAttr, Pac4jUserProfiles(value.asInstanceOf[util.LinkedHashMap[String, CommonProfile]]))
      case _ =>
        throw new NotImplementedError(s"setRequestAttribute for $name not implemented")
    }
  }

  override def getRequestHeader(name: String): String = request.headers.find(_.name == name).map(_.value).orNull

  override def getRequestMethod: String = request.method.name

  override def getRemoteAddr: String = request.remoteAddr.orNull

  override def writeResponseContent(content: String): Unit = {
    println("writeResponseContent !!!! TODO no unsafePerformSync! Set content")
    val contentType = response.contentType
    response = response.withBody(content).unsafePerformSync
    // withBody overwrites the contentType to text/plain. Set it back to what it was before.
    response = response.withContentType(contentType)
    println(s"writeResponseContent After Response: ${response.toString()}")
  }

  override def setResponseStatus(code: Int): Unit = {
    response = response.withStatus(Status.fromInt(code).getOrElse(Status.Ok))
  }

  override def setResponseHeader(name: String, value: String): Unit = {
    println(s"setResponseHeader $name = $value")
    response = response.putHeaders(Header(name, value))
    println(s"setResponseHeader Response: ${response.toString()}")
  }

  override def setResponseContentType(content: String): Unit = {
    println("setResponseContentType TODO: Parse " + content)
    response = response.withContentType(Some(`Content-Type`(MediaType.`text/html`, Some(Charset.`UTF-8`))))
    println(s"setResponseContentType Response: ${response.toString()}")
  }

  override def getServerName: String = request.serverAddr

  override def getServerPort: Int = request.serverPort

  override def getScheme: String = request.uri.scheme.map(_.value).orNull

  override def isSecure: Boolean = request.isSecure.getOrElse(false)

  override def getFullRequestURL: String = request.uri.toString()

  override def getRequestCookies: util.Collection[Cookie] = {
    val convertCookie = (c: org.http4s.Cookie) => new org.pac4j.core.context.Cookie(c.name, c.content)
    val cookies = CookieHeader.from(request.headers).map(_.values.map(convertCookie))
    cookies.map(_.list).getOrElse(Nil).asJavaCollection
  }

  override def addResponseCookie(cookie: Cookie): Unit = {
    val expires = if (cookie.getMaxAge == -1) {
      None
    } else {
      Some(HttpDate.unsafeFromEpochSecond(cookie.getMaxAge))
    }
    response = response.addCookie(cookie.getName, cookie.getValue, expires)
  }

  override def getPath: String = request.uri.path.toString

  override def getRequestContent: String = {
    request.bodyAsText.runLast.unsafePerformSync.orNull
  }

  override def getProtocol: String = request.uri.scheme.get.value

  def modifyResponse(f: Response => Response): Unit = {
    response = f(response)
  }

  def getRequest: Request = request

  def getResponse: Response = response
}
