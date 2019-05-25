package org.pac4j.http4s

import Generators._
import JsonHelpers.{json, jsonEncoder}
import Matchers._
import SessionSyntax._
import io.circe._
import io.circe.jawn.CirceSupportParser.facade
import io.circe.optics.all._
import io.circe.syntax._
import java.time.Instant

import monocle.Monocle
import monocle.Monocle._
import org.http4s.{RequestOps => _, _}
import org.http4s.dsl._
import org.http4s.headers.{`Content-Type`, `Set-Cookie`, Cookie => CookieHeader}
import org.http4s.util.NonEmptyList
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary
import org.specs2.ScalaCheck
import org.specs2.matcher.{Matcher, ResultMatchers, TaskMatchers}
import org.specs2.mutable.Specification

import scala.concurrent.duration._
import scalaz.Scalaz._
import scalaz.concurrent.Task

// Copyright 2013-2014 http4s [http://www.http4s.org]

object JsonHelpers {
  implicit val json: EntityDecoder[Json] = org.http4s.jawn.jawnDecoder(facade)
  def jsonOf[A](implicit decoder: Decoder[A]): EntityDecoder[A] =
    json.flatMapR { json =>
      decoder.decodeJson(json).fold(
        failure =>
          DecodeResult.failure(InvalidMessageBodyFailure(s"Could not decode JSON: $json", Some(failure))),
        DecodeResult.success(_)
      )
    }

  implicit val jsonEncoder: EntityEncoder[Json] =
    EntityEncoder[String].contramap[Json] { json =>
      Printer.noSpaces.pretty(json)
    }.withContentType(`Content-Type`(MediaType.`application/json`))
  def jsonEncoderOf[A](implicit encoder: Encoder[A]): EntityEncoder[A] =
    jsonEncoder.contramap[A](encoder.apply)
}

object Generators {
  implicit def arbSession: Arbitrary[Session] =
    Arbitrary(for {
      string <- Gen.alphaStr
      number <- arbitrary[Int]
    } yield Json.obj("string" -> string.asJson, "number" -> number.asJson))
}

object Matchers {
  import org.specs2.matcher.Matchers._

  def setCookies(mb: MaybeResponse): Vector[Cookie] =
    mb match {
      case response: Response =>
        response.headers.collect {
          case `Set-Cookie`(setCookie) if setCookie.cookie.expires.forall(i => !i.toInstant.isBefore(Instant.now())) =>
            //        _ >= java.time.Instant.now()) =>
            setCookie.cookie
        }.toVector
      case Pass =>
        Vector.empty
    }

  def expiredCookies(mb: MaybeResponse): Vector[Cookie] =
    mb match {
      case response: Response =>
        response.headers.collect {
          case `Set-Cookie`(setCookie) if setCookie.cookie.expires.exists(_.toInstant.isBefore(Instant.now())) =>
            setCookie.cookie
        }.toVector
      case Pass =>
        Vector.empty
    }

  def beCookieWithName(name: String): Matcher[Cookie] =
    be_===(name) ^^ ((_: Cookie).name)

  def beCookieWhoseContentContains(subcontent: String): Matcher[Cookie] =
    contain(subcontent) ^^ ((_: Cookie).content)

  def haveSetCookie(cookieName: String): Matcher[MaybeResponse] =
    contain(beCookieWithName(cookieName)) ^^ setCookies _

  def haveClearedCookie(cookieName: String): Matcher[MaybeResponse] =
    contain(beCookieWithName(cookieName)) ^^ expiredCookies _

  def haveStatus(status: Status): Matcher[MaybeResponse] =
    be_===(status) ^^ { f: MaybeResponse =>
      f match {
        case r: Response => r.status
        case Pass => ???
      }
    }

  def haveBody[A: EntityDecoder](a: A): Matcher[MaybeResponse] =
    TaskMatchers.returnValue(be_===(a)) ^^ { f: MaybeResponse =>
      f match {
        case r: Response => r.as[A]
        case Pass => ???
      }
    }
}

object SessionSpec extends Specification with ScalaCheck with TaskMatchers with ResultMatchers {
  val config = SessionConfig(
    cookieName = "session",
    mkCookie = Cookie(_, _),
    secret = "this is a secret",
    maxAge = 5.minutes
  )

  val newSession = Json.obj("created" -> true.asJson)

  "session management" should {
    def sut: HttpService =
      Session.sessionManagement(config)(
        HttpService {
          case GET -> Root / "id" =>
            Ok()

          case GET -> Root / "create" =>
            Ok().newSession(newSession)

          case GET -> Root / "clear" =>
            Ok().clearSession

          case req@GET -> Root / "read" =>
            for {
              session <- req.session
              response <- session.cata(Ok(_), NotFound())
            } yield response

          case GET -> Root / "modify" =>
            val _number = jsonObject ^|-> at[JsonObject, String, Option[Json]]("number") ^<-? Monocle.some ^<-? jsonInt
            Ok().modifySession(_number.modify(_ + 1))
        }
      )

    "Doing nothing" should {
      "not clear or set a session cookie when there is no session" in {
        val request = Request(Method.GET, uri("/id"))
        sut(request) must returnValue(not(haveSetCookie(config.cookieName) or haveClearedCookie(config.cookieName)))
      }

      "not clear a session cookie when one is set" in prop { session: Session =>
        val response = for {
          cookie <- config.cookie(session.noSpaces)
          request = Request(Method.GET, uri("/id")).putHeaders(CookieHeader(NonEmptyList(cookie)))
          response <- sut(request)
        } yield response
        response must returnValue(not(haveClearedCookie(config.cookieName)))
      }
    }

    "Creating a session" should {
      "set a session cookie as per mkCookie" in {
        val request = Request(Method.GET, uri("/create"))
        // Explicitly uses unsafePerformSync to help with race
        val response = sut(request).unsafePerformSync
        setCookies(response.orNotFound) must contain(config.cookie(newSession.noSpaces).unsafePerformSync)
      }

      "not include the session data in a readable form in the cookie" in {
        val request = Request(Method.GET, uri("/create"))
        sut(request).map(setCookies) must returnValue(not(contain(beCookieWhoseContentContains("created"))))
      }
    }

    "Clearing a session" should {
      "clear session cookie when one is set" in prop { session: Session =>
        val response = for {
          cookie <- config.cookie(session.noSpaces)
          request = Request(Method.GET, uri("/clear")).putHeaders(CookieHeader(NonEmptyList(cookie)))
          response <- sut(request)
        } yield response
        response must returnValue(haveClearedCookie(config.cookieName))
      }

      "do nothing when one is not set" in {
        val request = Request(Method.GET, uri("/clear"))
        val response = sut(request)
        response must returnValue(not(haveSetCookie(config.cookieName) or haveClearedCookie(config.cookieName)))
      }
    }

    "Reading a session" should {
      "read None when there is no session" in {
        val request = Request(Method.GET, uri("/read"))
        sut(request) must returnValue(haveStatus(Status.NotFound))
      }

      "read None when the session is signed with a different secret" in prop { session: Session =>
        val response = for {
          cookie <- config.copy(secret = "this is a different secret").cookie(session.noSpaces)
          request = Request(Method.GET, uri("/read")).putHeaders(CookieHeader(NonEmptyList(cookie)))
          response <- sut(request)
        } yield response
        response must returnValue(haveStatus(Status.NotFound))
      }

      "read None when the session has expired" in prop { session: Session =>
        val response = for {
          cookie <- config.copy(maxAge = 0.seconds).cookie(session.noSpaces)
          request = Request(Method.GET, uri("/read")).putHeaders(CookieHeader(NonEmptyList(cookie)))
          response <- sut(request)
        } yield response
        response must returnValue(haveStatus(Status.NotFound))
      }

      "read the session when it exists" in prop { session: Session =>
        val response = for {
          cookie <- config.cookie(session.noSpaces)
          request = Request(Method.GET, uri("/read")).putHeaders(CookieHeader(NonEmptyList(cookie)))
          response <- sut(request)
        } yield response
        response must returnValue(haveBody(session))
      }
    }

    "Modifying a session" should {
      "update the session when set" in {
        def unsafeToNel[A](as: List[A]): NonEmptyList[A] =
          NonEmptyList.nel(as.head, as.tail)
        val response = for {
          cookie <- config.cookie(Json.obj("number" -> 0.asJson).noSpaces)
          firstRequest = Request(Method.GET, uri("/modify")).putHeaders(CookieHeader(NonEmptyList(cookie)))
          firstResponse <- sut(firstRequest)
          cookies = setCookies(firstResponse)
          secondRequest = Request(Method.GET, uri("/read")).putHeaders(CookieHeader(unsafeToNel(cookies.toList)))
          secondResponse <- sut(secondRequest)
        } yield secondResponse
        response must returnValue(haveBody(Json.obj("number" -> 1.asJson)))
      }

      "do nothing when not" in {
        val request = Request(Method.GET, uri("/modify"))
        sut(request) must returnValue(not(haveSetCookie(config.cookieName) or haveClearedCookie(config.cookieName)))
      }
    }
  }

  "session required" should {
    def fallback: Task[Response] =
      SeeOther(uri("/other"))
    def sut: HttpService =
      (Session.sessionManagement(config) compose Session.sessionRequired(fallback))(HttpService {
        case GET -> Root =>
          Ok()
      })

    "allow access to the service when a session is set" in prop { session: Session =>
      val response = for {
        cookie <- config.cookie(session.noSpaces)
        request = Request(Method.GET, uri("/")).putHeaders(CookieHeader(NonEmptyList(cookie)))
        response <- sut(request)
      } yield response
      response must returnValue(haveStatus(Status.Ok))
    }

    "use the fallback response when a session is not set" in {
      val request = Request(Method.GET, uri("/"))
      sut(request) must returnValue(haveStatus(Status.SeeOther))
    }
  }
}
