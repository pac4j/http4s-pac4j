package org.pac4j.http4s

import java.util

import org.http4s.{AttributeKey, Header, Request, Response, Status, MediaType}
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.context.{Cookie, WebContext}
import org.http4s.headers.`Content-Type`
import scala.collection.JavaConverters._

class Http4sWebContext(var request: Request, var response: Response) extends WebContext {
  override def getSessionStore: SessionStore[Http4sWebContext] = Http4sSessionStore

  override def getRequestParameter(name: String): String = request.params.get(name).orNull

  override def getRequestParameters: util.Map[String, Array[String]] = request.params.toSeq.map(a => (a._1, Array(a._2))).toMap.asJava

  override def getRequestAttribute(name: String): AnyRef = request.attributes

  override def setRequestAttribute(name: String, value: Any): Unit = ??? //request.wi .attributes.put(AttributeKey()(name), value)

  override def getRequestHeader(name: String): String = request.headers.find(_.name == name).map(_.value).orNull

  override def getRequestMethod: String = request.method.name

  override def getRemoteAddr: String = request.remoteAddr.orNull

  override def writeResponseContent(content: String): Unit = { response.withBody(content); () }

  override def setResponseStatus(code: Int): Unit = ??? //response.withStatus(Status(code))

  override def setResponseHeader(name: String, value: String): Unit = response.putHeaders(Header(name, value))

  override def setResponseContentType(content: String): Unit = ??? //response.withContentType(`Content-Type`. (MediaType.)

  override def getServerName: String = request.serverAddr

  override def getServerPort: Int = request.serverPort

  override def getScheme: String = request.uri.scheme.map(_.value).orNull

  override def isSecure: Boolean = request.isSecure.getOrElse(false)

  override def getFullRequestURL: String = request.uri.toString()

  override def getRequestCookies: util.Collection[Cookie] = ??? //request.h

  override def addResponseCookie(cookie: Cookie): Unit = ???

  override def getPath: String = request.uri.path.toString
}
