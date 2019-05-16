package org.pac4j.http4s

import java.util

import org.http4s
import org.http4s.{AttributeKey, Charset, Header, HttpDate, MediaType, Request, Response, Status, UrlForm}
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.context.{Cookie, Pac4jConstants, WebContext}
import org.http4s.headers.`Content-Type`
import org.http4s.headers.{Cookie => CookieHeader}
import org.pac4j.core.config.Config
import org.pac4j.core.profile.CommonProfile
import org.slf4j.LoggerFactory
import scalaz.{-\/, \/-}

import scala.collection.JavaConverters._

class Http4sWebContext(private var request: Request, private val sessionStore: SessionStore[Http4sWebContext]) extends WebContext {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private var response: Response = Response()

  case class Pac4jUserProfiles(pac4jUserProfiles: util.LinkedHashMap[String, CommonProfile])

  val pac4jUserProfilesAttr: AttributeKey[Pac4jUserProfiles] = AttributeKey[Pac4jUserProfiles]
  val sessionIdAttr: AttributeKey[String] = AttributeKey[String]

  override def getSessionStore: SessionStore[Http4sWebContext] = sessionStore

  override def getRequestParameter(name: String): String = {
    if (request.contentType.contains(`Content-Type`(MediaType.`application/x-www-form-urlencoded`))) {
      logger.debug(s"getRequestParameter: Getting from Url Encoded Form name=$name")
      UrlForm.decodeString(Charset.`UTF-8`)(getRequestContent) match {
        case -\/(err) => throw new Exception(err.toString)
        case \/-(urlForm) => urlForm.getFirstOrElse(name, request.params.get(name).orNull)
      }
    } else {
      logger.debug(s"getRequestParameter: Getting from query params name=$name")
      request.params.get(name).orNull
    }
  }

  override def getRequestParameters: util.Map[String, Array[String]] = {
    logger.debug(s"getRequestParameters")
    request.params.toSeq.map(a => (a._1, Array(a._2))).toMap.asJava
  }

  override def getRequestAttribute(name: String): AnyRef = {
    logger.debug(s"getRequestAttribute: $name")
    name match {
      case "pac4jUserProfiles" =>
        request.attributes.get(pac4jUserProfilesAttr).orNull
      case Pac4jConstants.SESSION_ID =>
        request.attributes.get(sessionIdAttr).orNull
      case _ =>
        throw new NotImplementedError(s"getRequestAttribute for $name not implemented")
    }
  }

  override def setRequestAttribute(name: String, value: Any): Unit = {
    logger.debug(s"setRequestAttribute: $name")
    request = name match {
      case "pac4jUserProfiles" =>
        request.withAttribute(pac4jUserProfilesAttr, Pac4jUserProfiles(value.asInstanceOf[util.LinkedHashMap[String, CommonProfile]]))
      case Pac4jConstants.SESSION_ID =>
        request.withAttribute(sessionIdAttr, value.asInstanceOf[String])
      case _ =>
        throw new NotImplementedError(s"setRequestAttribute for $name not implemented")
    }
  }

  override def getRequestHeader(name: String): String = request.headers.find(_.name == name).map(_.value).orNull

  override def getRequestMethod: String = request.method.name

  override def getRemoteAddr: String = request.remoteAddr.orNull

  override def writeResponseContent(content: String): Unit = {
    logger.debug("writeResponseContent")
    val contentType = response.contentType
    modifyResponse { r =>
      r.withBody(content).unsafePerformSync
        // withBody overwrites the contentType to text/plain. Set it back to what it was before.
        .withContentType(contentType)
    }
  }

  override def setResponseStatus(code: Int): Unit = {
    logger.debug(s"setResponseStatus $code")
    modifyResponse { r =>
      r.withStatus(Status.fromInt(code).getOrElse(Status.Ok))
    }
  }

  override def setResponseHeader(name: String, value: String): Unit = {
    logger.debug(s"setResponseHeader $name = $value")
    modifyResponse { r =>
      r.putHeaders(Header(name, value))
    }
  }

  override def setResponseContentType(content: String): Unit = {
    logger.debug("setResponseContentType: " + content)
    // TODO Parse the input
    modifyResponse { r =>
      r.withContentType(Some(`Content-Type`(MediaType.`text/html`, Some(Charset.`UTF-8`))))
    }
  }

  override def getServerName: String = request.serverAddr

  override def getServerPort: Int = request.serverPort

  override def getScheme: String = request.uri.scheme.map(_.value).orNull

  override def isSecure: Boolean = request.isSecure.getOrElse(false)

  override def getFullRequestURL: String = request.uri.toString()

  override def getRequestCookies: util.Collection[Cookie] = {
    logger.debug("getRequestCookies")
    val convertCookie = (c: org.http4s.Cookie) => new org.pac4j.core.context.Cookie(c.name, c.content)
    val cookies = CookieHeader.from(request.headers).map(_.values.map(convertCookie))
    cookies.map(_.list).getOrElse(Nil).asJavaCollection
  }

  override def addResponseCookie(cookie: Cookie): Unit = {
    logger.debug("addResponseCookie")
    val expires = if (cookie.getMaxAge == -1) {
      None
    } else {
      Some(HttpDate.unsafeFromEpochSecond(cookie.getMaxAge))
    }
    val http4sCookie = http4s.Cookie(cookie.getName, cookie.getValue, expires, path=Option(cookie.getPath))
    response = response.addCookie(http4sCookie)
  }

  def removeResponseCookie(name: String): Unit = {
    logger.debug("removeResponseCookie")
    response = response.removeCookie(name)
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

object Http4sWebContext {
  def apply(request: Request, config: Config) =
    new Http4sWebContext(request, config.getSessionStore.asInstanceOf[SessionStore[Http4sWebContext]])
}
