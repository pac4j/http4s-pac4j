package org.pac4j.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.EntityDecoder
import org.http4s.Response
import org.http4s.ResponseCookie
import org.http4s.Status
import org.specs2.matcher.Matcher

import java.time.Instant

object CookieMatchers {
  type Cookie = ResponseCookie
  import org.specs2.matcher.Matchers._

  def setCookies(resp: Response[IO]): List[Cookie] = resp
    .cookies
    .filter(_.expires.forall(i => !i.toInstant.isBefore(Instant.now())))

  def expiredCookies(resp: Response[IO]): List[Cookie] = resp
    .cookies
    .filter(_.expires.exists(_.toInstant.isBefore(Instant.now())))

  def beCookieWithName(name: String): Matcher[Cookie] =
    be_===(name) ^^ ((_: Cookie).name)

  def beCookieWhoseContentContains(subcontent: String): Matcher[Cookie] =
    contain(subcontent) ^^ ((_: Cookie).content)

  def haveSetCookie(cookieName: String): Matcher[Response[IO]] =
    contain(beCookieWithName(cookieName)) ^^ setCookies _

  def haveClearedCookie(cookieName: String): Matcher[Response[IO]] =
    contain(beCookieWithName(cookieName)) ^^ expiredCookies _

  def haveStatus(status: Status): Matcher[Response[IO]] =
    be_===(status) ^^ { (r: Response[IO]) => r.status }

  def haveBody[A](a: A)(implicit d: EntityDecoder[IO, A]): Matcher[Response[IO]] =
    be_===(a) ^^ { (r: Response[IO]) => r.as[A].unsafeRunSync() }
}
