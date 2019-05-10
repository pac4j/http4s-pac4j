package com.test

import org.http4s._
import org.http4s.dsl._

import scala.concurrent.duration._
import org.http4s.server.blaze._
import org.http4s.util.ProcessApp
import org.pac4j.http4s.{CallbackService, SecurityFilterMiddleware, Session, SessionConfig}
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalatags.Text.all._
import org.pac4j.http4s.SessionSyntax._

object Main extends ProcessApp {
  import ScalatagsInstances._

  val sessionConfig = SessionConfig(
    cookieName = "session",
    mkCookie = Cookie(_, _),
    secret = "This is a secret",
    maxAge = 5.minutes
  )

  val config = new DemoConfigFactory().build()
  val callbackService = new CallbackService(config)

  val root = HttpService {
    case req @ GET -> Root =>
      val profiles = req.session2.map( s => p("Session: ", s.toString())).getOrElse(p("No Session"))
      Ok(html(
        body(
          h1("index"),
          a(href:="/facebook")("Protected url by Facebook: /facebook"), "use a real account", br(),
          a(href:="/saml2")("Protected url by SAML2: /saml2"), "use testpac4j at gmail.com / Pac4jtest", br(),
          p(),
          profiles
        )
      ))
    case GET -> Root / "loginForm" =>
      Ok(
        form(action:="http://localhost:8080/callback?client_name=FormClient", method:="POST")(
          input(`type`:="text", name:="username", value:="")(),
          p(),
          input(`type`:="password", name:="password", value:="")(),
          p(),
          input(`type`:="submit", name:="submit", value:="Submit")()
      ))
    case GET -> Root / "favicon.ico" =>
      NotFound()
    case req @ GET -> Root / "callback" =>
      callbackService.login(req)
    case req @ POST -> Root / "callback" =>
      callbackService.login(req)
  }

  val protectedPages = HttpService {
    case GET -> Root =>
      println("****** Protected Page Rendering")
      Ok(s"Protected Page")
  }

  val authedProtectedPages: Service[Request, MaybeResponse] =
    Session.sessionManagement(sessionConfig)
      .compose(SecurityFilterMiddleware.securityFilter(config))
      .apply(protectedPages)

  override def process(args: List[String]): Process[Task, Nothing] = {
    BlazeBuilder
      .bindHttp(8080, "localhost")
      .mountService(authedProtectedPages, "/protected")
      .mountService(Session.sessionManagement(sessionConfig).apply(root), "/")
      .serve
  }
}
