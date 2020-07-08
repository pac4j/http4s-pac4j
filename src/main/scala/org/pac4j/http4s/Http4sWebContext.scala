package org.pac4j.http4s

import java.util

import cats.implicits._
import cats.effect.{IO, SyncIO}
import org.http4s._
import io.chrisdavenport.vault.Key
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.context.{Cookie, Pac4jConstants, WebContext}
import org.http4s.headers.`Content-Type`
import org.http4s.headers.{Cookie => CookieHeader}
import org.pac4j.core.config.Config
import org.pac4j.core.profile.CommonProfile
import org.slf4j.LoggerFactory
import fs2.Collector

import scala.collection.JavaConverters._

/**
  * Http4sWebContext is the adapter layer to allow Pac4j to interact with
  * Http4s request and response objects.
  *
  * @param request Http4s request object currently being handled
  * @param sessionStore User session information
  *
  * @author Iain Cardnell
  */
class Http4sWebContext(
    private var request: Request[IO],
    private val sessionStore: SessionStore[Http4sWebContext],
  ) extends WebContext {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private var response: Response[IO] = Response()

  case class Pac4jUserProfiles(pac4jUserProfiles: util.LinkedHashMap[String, CommonProfile])

  val pac4jUserProfilesAttr: Key[Pac4jUserProfiles] =
    Key.newKey[SyncIO, Pac4jUserProfiles].unsafeRunSync
  val sessionIdAttr: Key[String] =
    Key.newKey[SyncIO, String].unsafeRunSync

  override def getSessionStore: SessionStore[Http4sWebContext] = sessionStore

  override def getRequestParameter(name: String): String = {
    if (request.contentType.contains(`Content-Type`(MediaType.application.`x-www-form-urlencoded`))) {
      logger.debug(s"getRequestParameter: Getting from Url Encoded Form name=$name")
      UrlForm.decodeString(Charset.`UTF-8`)(getRequestContent) match {
        case Left(err) => throw new Exception(err.toString)
        case Right(urlForm) => urlForm.getFirstOrElse(name, request.params.get(name).orNull)
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
        request.attributes.lookup(pac4jUserProfilesAttr).orNull
      case Pac4jConstants.SESSION_ID =>
        request.attributes.lookup(sessionIdAttr).orNull
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
    modifyResponse{ rsp => contentType.fold(
        rsp.withEntity(content)
      )(
        // withBody overwrites the contentType to text/plain. Set it back to what it was before.
        rsp.withEntity(content).withContentType(_)
      )
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
      r.withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
    }
  }

  override def getServerName: String = request.serverAddr

  override def getServerPort: Int = request.serverPort

  override def getScheme: String = request.uri.scheme.map(_.value).orNull

  override def isSecure: Boolean = request.isSecure.getOrElse(false)

  override def getFullRequestURL: String = request.uri.toString()

  override def getRequestCookies: util.Collection[Cookie] = {
    logger.debug("getRequestCookies")
    val convertCookie = (c: RequestCookie) => new org.pac4j.core.context.Cookie(c.name, c.content)
    val cookies = request.cookies.map(convertCookie)
    cookies.asJavaCollection
  }

  override def addResponseCookie(cookie: Cookie): Unit = {
    logger.debug("addResponseCookie")
    val expires = if (cookie.getMaxAge == -1) {
      None
    } else {
      Some(HttpDate.unsafeFromEpochSecond(cookie.getMaxAge))
    }
    val http4sCookie = ResponseCookie(cookie.getName, cookie.getValue, expires, path=Option(cookie.getPath))
    response = response.addCookie(http4sCookie)
  }

  def removeResponseCookie(name: String): Unit = {
    logger.debug("removeResponseCookie")
    response = response.removeCookie(name)
  }

  override def getPath: String = request.uri.path.toString

  override def getRequestContent: String = {
    request.bodyText.compile.to(Collector.string).unsafeRunSync
  }

  override def getProtocol: String = request.uri.scheme.get.value

  def modifyResponse(f: Response[IO] => Response[IO]): Unit = {
    response = f(response)
  }

  def getRequest: Request[IO] = request

  def getResponse: Response[IO] = response
}

object Http4sWebContext {
  def apply(request: Request[IO], config: Config) =
    new Http4sWebContext(request, config.getSessionStore.asInstanceOf[SessionStore[Http4sWebContext]])
}
