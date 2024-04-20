package org.pac4j.http4s

import java.util
import cats.effect.{IO, Sync, SyncIO}
import cats.effect.std.Dispatcher
import cats.syntax.eq._
import org.http4s._
import org.pac4j.core.context.{Cookie, WebContext}
import org.http4s.headers.`Content-Type`
import org.pac4j.core.config.Config
import org.pac4j.core.profile.CommonProfile
import org.slf4j.LoggerFactory
import fs2.Collector
import org.http4s.Header.Raw
import org.pac4j.core.util.Pac4jConstants
import org.typelevel.ci.CIString
import org.typelevel.vault.Key

import java.nio.charset.StandardCharsets
import java.util.Optional
import scala.jdk.CollectionConverters._
import org.http4s.SameSite

/**
  * Http4sWebContext is the adapter layer to allow Pac4j to interact with
  * Http4s request and response objects.
  *
  * @param request Http4s request object currently being handled
  * @param bodyExtractor function to extract the body from F[]
  *
  * @author Iain Cardnell
  */
class Http4sWebContext[F[_]: Sync](
    private var request: Request[F],
    private val bodyExtractor: F[String] => String,
  ) extends WebContext {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private var response: Response[F] = Response()

  type Pac4jUserProfiles = util.LinkedHashMap[String, CommonProfile]

  val pac4jUserProfilesAttr: Key[Pac4jUserProfiles] =
    Key.newKey[SyncIO, Pac4jUserProfiles].unsafeRunSync()
  val sessionIdAttr: Key[String] =
    Key.newKey[SyncIO, String].unsafeRunSync()
  val pac4jCsrfTokenAttr: Key[String] =
    Key.newKey[SyncIO, String].unsafeRunSync()
  val pac4jPreviousCsrfTokenAttr: Key[String] =
    Key.newKey[SyncIO, String].unsafeRunSync()
  val otherPac4jAttr: Key[Map[String, String]] =
    Key.newKey[SyncIO, Map[String, String]].unsafeRunSync()

  override def getRequestParameter(name: String): Optional[String] = {
    if (request.contentType.contains(`Content-Type`(MediaType.application.`x-www-form-urlencoded`))) {
      logger.debug(s"getRequestParameter: Getting from Url Encoded Form name=$name")
      UrlForm.decodeString(Charset.`UTF-8`)(getRequestContent) match {
        case Left(err) => throw new Exception(err.toString)
        case Right(urlForm) => Optional.ofNullable(urlForm.getFirstOrElse(name, request.params.get(name).orNull))
      }
    } else {
      logger.debug(s"getRequestParameter: Getting from query params name=$name")
      Optional.ofNullable(request.params.get(name).orNull)
    }
  }

  override def getRequestParameters: util.Map[String, Array[String]] = {
    if (request.contentType.contains(`Content-Type`(MediaType.application.`x-www-form-urlencoded`))) {
      logger.debug("getRequestParameters: Getting from Url Encoded Form")
      UrlForm.decodeString(Charset.`UTF-8`)(getRequestContent) match {
        case Left(err) => throw new Exception(err.toString)
        case Right(urlForm) => urlForm.values.map(a => (a._1, a._2.iterator.toArray)).asJava
      }
    } else {
      logger.debug("getRequestParameters: Getting from query params")
      request.params.map(a => (a._1, Array(a._2))).asJava
    }
  }

  override def getRequestAttribute(name: String): Optional[AnyRef] = {
    logger.debug(s"getRequestAttribute: $name")
    name match {
      case Pac4jConstants.USER_PROFILES =>
        Optional.ofNullable(request.attributes.lookup(pac4jUserProfilesAttr).orNull)
      case Pac4jConstants.SESSION_ID =>
        Optional.ofNullable(request.attributes.lookup(sessionIdAttr).orNull)
      case Pac4jConstants.CSRF_TOKEN =>
        Optional.ofNullable(request.attributes.lookup(pac4jCsrfTokenAttr).orNull)
      case Pac4jConstants.PREVIOUS_CSRF_TOKEN =>
        Optional.ofNullable(request.attributes.lookup(pac4jPreviousCsrfTokenAttr).orNull)
      case other =>
        Optional.ofNullable(request.attributes.lookup(otherPac4jAttr).flatMap(_.get(other)).orNull)
    }
  }

  override def setRequestAttribute(name: String, value: Any): Unit = {
    logger.debug(s"setRequestAttribute: $name")
    request = name match {
      case Pac4jConstants.USER_PROFILES =>
        request.withAttribute(pac4jUserProfilesAttr, value.asInstanceOf[Pac4jUserProfiles])
      case Pac4jConstants.SESSION_ID =>
        request.withAttribute(sessionIdAttr, value.asInstanceOf[String])
      case Pac4jConstants.CSRF_TOKEN =>
        request.withAttribute(pac4jCsrfTokenAttr, value.asInstanceOf[String])
      case Pac4jConstants.PREVIOUS_CSRF_TOKEN =>
        request.withAttribute(pac4jPreviousCsrfTokenAttr, value.asInstanceOf[String])
      case other =>
        val old = request.attributes.lookup(otherPac4jAttr).getOrElse(Map.empty[String, String])
        request.withAttribute(otherPac4jAttr, old + (other -> value.asInstanceOf[String]))
    }
  }

  override def getRequestHeader(name: String): Optional[String] = Optional.ofNullable(request.headers.get(CIString(name)).map(_.head.value).orNull)

  override def getRequestMethod: String = request.method.name

  override def getRemoteAddr: String = request.remoteAddr.map(_.toInetAddress.getHostName).orNull

  override def setResponseHeader(name: String, value: String): Unit = {
    logger.debug(s"setResponseHeader $name = $value")
    modifyResponse { r =>
      r.putHeaders(Raw(CIString(name), value))
    }
  }

  override def setResponseContentType(content: String): Unit = {
    logger.debug("setResponseContentType: " + content)
    // TODO Parse the input
    modifyResponse { r =>
      r.withContentType(`Content-Type`(MediaType.text.html, Some(Charset.`UTF-8`)))
    }
  }

  override def getServerName: String = request.serverAddr.map(_.toInetAddress.getHostName).orNull

  override def getServerPort: Int = request.serverPort.map(_.value).getOrElse(0)

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
    val maxAge = Option(cookie.getMaxAge).filter(_ =!= -1).map(_.toLong)
    val sameSite = Option(cookie.getSameSitePolicy()).map(_.toLowerCase()).map {
      case "strict" => SameSite.Strict
      case "lax" => SameSite.Lax
      case _ => SameSite.None
    }

    val http4sCookie = ResponseCookie(
      name = cookie.getName,
      content = cookie.getValue,
      maxAge = maxAge,
      domain = Option(cookie.getDomain),
      path = Option(cookie.getPath),
      secure = cookie.isSecure,
      httpOnly = cookie.isHttpOnly,
      sameSite = sameSite,
      // - `RequestCookie.extension` has no counterpart in `Cookie`;
      // - `Cookie.getComment` can be passed via `extension`, but it's not worth
      // the trouble.
    )
    response = response.addCookie(http4sCookie)
  }

  def removeResponseCookie(name: String): Unit = {
    logger.debug("removeResponseCookie")
    response = response.removeCookie(name)
  }

  override def getPath: String = request.uri.path.toString

  override lazy val getRequestContent: String =
    bodyExtractor(request.bodyText.compile.to(Collector.string))

  override def getProtocol: String = request.uri.scheme.get.value

  def setResponseStatus(code: Int): Unit = {
    logger.debug(s"setResponseStatus $code")
    modifyResponse { r =>
      r.withStatus(Status.fromInt(code).getOrElse(Status.Ok))
    }
  }

  def setContentType(contentType: `Content-Type`): Unit = {
    logger.debug(s"setContentType $contentType")
    modifyResponse { r =>
      r.withContentType(contentType)
    }
  }

  def setContent(content: String): Unit = {
    logger.debug(s"setContent $content")
    modifyResponse { r =>
      r.withEntity(content.getBytes(StandardCharsets.UTF_8))
    }
  }

  def modifyResponse(f: Response[F] => Response[F]): Unit = {
    response = f(response)
  }

  def getRequest: Request[F] = request

  def getResponse: Response[F] = response

  override def getResponseHeader(name: String): Optional[String] =
    Optional.ofNullable(response.headers.get(CIString(name)).map(_.head.value).orNull)
}

object Http4sWebContext {

  /** @deprecated
   *  Use withDispatcherInstance
   */
  def ioInstance(request: Request[IO], config: Config) = {
    import cats.effect.unsafe.implicits.global
    new Http4sWebContext[IO](request, _.unsafeRunSync())
  }

  def withDispatcherInstance[F[_]: Sync](dispatcher: Dispatcher[F])(request: Request[F]) =
    new Http4sWebContext[F](request, dispatcher.unsafeRunSync)
}
