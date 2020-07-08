package org.pac4j.http4s

import cats.implicits._
import cats.effect.IO
import Generators._
import Matchers._
import SessionSyntax._
import io.circe._
import io.circe.jawn.CirceSupportParser.facade
import io.circe.optics.all._
import io.circe.syntax._
import java.time.Instant

import monocle.Monocle
import monocle.Monocle._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.headers.{`Content-Type`, `Set-Cookie`, Cookie => CookieHeader}
import org.http4s.implicits._
import org.http4s.circe._
import cats.data.NonEmptyList
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary
import org.specs2.ScalaCheck
import org.specs2.matcher.{Matcher, IOMatchers}
import org.specs2.mutable.Specification
import org.specs2.concurrent.ExecutionEnv
import mouse.option._
import cats.data.OptionT

import scala.concurrent.duration._

// Copyright 2013-2014 http4s [http://www.http4s.org]

object Generators {
  implicit def arbSession: Arbitrary[Session] =
    Arbitrary(for {
      string <- arbitrary[String]
      number <- arbitrary[Int]
    } yield Json.obj("string" -> string.asJson, "number" -> number.asJson))
}

object Matchers {
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
    be_===(a) ^^ { (r: Response[IO]) => r.as[A].unsafeRunSync }
}

class SessionSpec(val exEnv: ExecutionEnv) extends Specification with ScalaCheck with IOMatchers {
  implicit class ResponseCookieOps(c: ResponseCookie) {
    def toRequestCookie: RequestCookie = RequestCookie(c.name, c.content)
  }
  import implicits._
  implicit val cs = IO.contextShift(exEnv.executionContext)
  implicit val ti = IO.timer(exEnv.executionContext)

  val config = SessionConfig(
    cookieName = "session",
    mkCookie = ResponseCookie(_, _),
    secret = "this is a secret",
    maxAge = 5.minutes
  )

  val newSession = Json.obj("created" -> true.asJson)

  "session management" should {
    def sut: HttpApp[IO] =
      Session.sessionManagement(config)(
        HttpService {
          case GET -> Root / "id" =>
            Ok()

          case GET -> Root / "create" =>
            Ok().map(_.newSession(newSession))

          case GET -> Root / "clear" =>
            Ok().map(_.clearSession)

          case req@GET -> Root / "read" => req.session.cata(Ok(_), NotFound())

          case GET -> Root / "modify" =>
            val _number = jsonObject ^|-> at[JsonObject, String, Option[Json]]("number") ^<-? Monocle.some ^<-? jsonInt
            Ok().map(_.modifySession(_number.modify(_ + 1)))
        }
      ).orNotFound

    "Doing nothing" should {
      "not clear or set a session cookie when there is no session" in {
        val request = Request[IO](Method.GET, uri"/id")
        sut(request) must returnValue(not(haveSetCookie(config.cookieName) or haveClearedCookie(config.cookieName)))
      }

      "not clear a session cookie when one is set" in prop { session: Session =>
        config.cookie[IO](session.noSpaces).map(cookie =>
            Request[IO](Method.GET, uri"/id").addCookie(cookie.toRequestCookie)
         ) flatMap(sut(_)) must returnValue(not(haveClearedCookie(config.cookieName)))
      }
    }

    "Creating a session" should {
      "set a session cookie as per mkCookie" in prop { session: Session =>
        val request = Request[IO](Method.GET, uri"/create")
        sut(request).map(setCookies _) must
          returnValue(contain(beCookieWithName(config.cookieName)))
      }

      "not include the session data in a readable form in the cookie" in {
        val request = Request[IO](Method.GET, uri"/create")
        sut(request).map(setCookies _) must
          returnValue(not(contain(beCookieWhoseContentContains("created"))))
      }
    }

    "Clearing a session" should {
      "clear session cookie when one is set" in prop { session: Session =>
        config.cookie[IO](session.noSpaces).map(cookie =>
          Request[IO](Method.GET, uri"/clear").addCookie(cookie.toRequestCookie)
        ).flatMap(sut(_)) must returnValue(haveClearedCookie(config.cookieName))
      }

      "do nothing when one is not set" in {
        val request = Request[IO](Method.GET, uri"/clear")
        val response = sut(request)
        response must returnValue(not(haveSetCookie(config.cookieName) or haveClearedCookie(config.cookieName)))
      }
    }

    "Reading a session" should {
      "read None when there is no session" in {
        val request = Request[IO](Method.GET, uri"/read")
        sut(request) must returnValue(haveStatus(Status.NotFound))
      }

      "read None when the session is signed with a different secret" in prop { session: Session =>
         config
           .copy(secret = "this is a different secret")
           .cookie[IO](session.noSpaces)
           .map(cookie =>
             Request[IO](Method.GET, uri"/read").addCookie(cookie.toRequestCookie)
           ).flatMap(sut(_)) must returnValue(haveStatus(Status.NotFound))
      }

      "read None when the session has expired" in prop { session: Session =>
        config.copy(maxAge = 0.seconds).cookie[IO](session.noSpaces).map(cookie =>
          Request[IO](Method.GET, uri"/read").addCookie(cookie.toRequestCookie)
        ).flatMap(sut(_)) must returnValue(haveStatus(Status.NotFound))
      }

      "read the session when it exists" in prop { session: Session =>
        config.cookie[IO](session.noSpaces).map(cookie =>
          Request[IO](Method.GET, uri"/read").addCookie(cookie.toRequestCookie)
        ).flatMap(sut(_)) must returnValue(haveStatus(Status.Ok) and haveBody(session))
      }
    }

    "Modifying a session" should {
      "update the session when set" in {
        def unsafeToNel[A](as: List[A]): NonEmptyList[A] = NonEmptyList(as.head, as.tail)
        val response = for {
          cookie <- config
            .cookie[IO](Json.obj("number" -> 0.asJson).noSpaces)
          firstRequest <- Request[IO](Method.GET, uri"/modify")
            .addCookie(cookie.toRequestCookie)
            .pure[IO]
          firstResponse <- sut(firstRequest)
          cookies = setCookies(firstResponse)
          secondRequest = cookies.foldLeft(
            Request[IO](Method.GET, uri"/read")
          )(
            (r: Request[IO], c: ResponseCookie) => r.addCookie(c.toRequestCookie)
          )
          secondResponse <- sut(secondRequest)
        } yield secondResponse
        response must returnValue(haveBody(Json.obj("number" -> 1.asJson)))
      }

      "do nothing when not" in {
        val request = Request[IO](Method.GET, uri"/modify")
        sut(request) must returnValue(not(haveSetCookie(config.cookieName) or haveClearedCookie(config.cookieName)))
      }
    }
  }

  "session required" should {
    def fallback: IO[Response[IO]] = SeeOther(uri"/other")
    def sut: HttpApp[IO] =
      (Session.sessionManagement(config) compose Session.sessionRequired(fallback))(HttpService {
        case GET -> Root =>
          Ok()
      }).orNotFound

    "allow access to the service when a session is set" in prop { session: Session =>
      config.cookie[IO](session.noSpaces).map(cookie =>
        Request[IO](Method.GET, uri"/").addCookie(cookie.toRequestCookie)
      ).flatMap(sut(_)) must returnValue(haveStatus(Status.Ok))
    }

    "use the fallback response when a session is not set" in {
      val request = Request[IO](Method.GET, uri"/")
      sut(request) must returnValue(haveStatus(Status.SeeOther))
    }
  }
}
