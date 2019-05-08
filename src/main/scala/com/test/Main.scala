package com.test

import org.http4s._
import org.http4s.dsl._

import scala.concurrent.duration._
import org.http4s.server.syntax._
import org.http4s.server.blaze._
import org.http4s.util.ProcessApp
import org.pac4j.http4s.{CallbackService, SecurityFilterMiddleware, Session, SessionConfig}
import scalaz.concurrent.Task
import scalaz.stream.Process

object Main extends ProcessApp {

  val sessionConfig = SessionConfig(
    cookieName = "session",
    mkCookie = Cookie(_, _),
    secret = "This is a secret",
    maxAge = 5.minutes
  )

  val config = new DemoConfigFactory().build()
  val callbackService = new CallbackService(config)

  val root = HttpService {
    case GET -> Root =>
      Ok(s"Hello World")
  }

  val protectedPages = HttpService {
    case GET -> Root / "protected" =>
      Ok() // (s"Done")
  }

  val authedProtectedPages: Service[Request, MaybeResponse] =
    Session.sessionManagement(sessionConfig)
      .andThen(SecurityFilterMiddleware.securityFilter(config))
      .apply(protectedPages)


  val loginCallBack = HttpService {
    case req @ GET -> Root / "login" =>
      callbackService.login(req)
    case req @ POST -> Root / "login" =>
      callbackService.login(req)
  }

  override def process(args: List[String]): Process[Task, Nothing] = {
    BlazeBuilder
      .bindHttp(8080, "localhost")
      .mountService(root, "/")
      .mountService(authedProtectedPages, "/")
      .mountService(loginCallBack, "/")
      .serve
  }
}
