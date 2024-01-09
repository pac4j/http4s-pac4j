package org.pac4j.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global

import org.http4s._
import org.specs2.mutable.Specification
import org.specs2.concurrent.ExecutionEnv

import org.pac4j.core.context.Cookie

class Http4sWebContextSpec(val exEnv: ExecutionEnv) extends Specification {

  import implicits._
  "Http4WebContextContext" should {

    "forward the cookie attributes" in {
      val request = Request[IO](Method.GET, uri"/id")
      val webContext = new Http4sWebContext[IO](request, _.unsafeRunSync())
      val cookie = new Cookie("cookie_name", "cookie_value")
      cookie.setMaxAge(1234)
      cookie.setDomain("www.example.com")
      cookie.setPath("/example/path")
      cookie.setSecure(true)
      cookie.setHttpOnly(true)
      cookie.setSameSitePolicy("Lax")
      webContext.addResponseCookie(cookie)
      val responseCookie = webContext.getResponse.cookies.find(cookie => cookie.name == cookie.name)
      val expectedCookie = ResponseCookie(
        name = cookie.getName(), 
        content = cookie.getValue(), 
        maxAge = Some(cookie.getMaxAge()),
        domain = Some(cookie.getDomain()),
        path = Some(cookie.getPath()),
        sameSite = Some(SameSite.Lax),
        secure = cookie.isSecure(),
        httpOnly = cookie.isHttpOnly()
      )
      responseCookie must beSome(expectedCookie)
    }
  }
}
