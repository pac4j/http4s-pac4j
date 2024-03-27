package org.pac4j.http4s

import org.specs2.mutable.Specification
import cats.effect.testing.specs2.CatsEffect
import cats.effect.IO
import cats.effect.std.Dispatcher
import org.http4s.Request
import org.pac4j.core.util.Pac4jConstants

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

object Http4sGenericSessionStoreSpec extends Specification with CatsEffect {
  "getSessionId retrieves SessionId from requestAttribute" >> {
    Dispatcher.parallel[IO].use { dispatcher =>
      val expectedSessionId = "sessionId"
      val sessionRepository = new CacheSessionRepository[IO]
      val underTest =
        new Http4sGenericSessionStore[IO](sessionRepository, dispatcher)()
      val request = Request[IO]()
      val webContext =
        new Http4sWebContext[IO](request, dispatcher.unsafeRunSync)
      webContext
        .setRequestAttribute(Pac4jConstants.SESSION_ID, expectedSessionId)
      val sessionId = underTest.getSessionId(webContext, false).toScala
      IO(sessionId must beSome(expectedSessionId))
    }
  }

  "getSessionId retrieves SessionId from cookie" >> {
    Dispatcher.parallel[IO].use { dispatcher =>
      val expectedSessionId = "sessionId"
      val sessionRepository = new CacheSessionRepository[IO]
      val underTest =
        new Http4sGenericSessionStore[IO](sessionRepository, dispatcher)()
      val request =
        Request[IO]().addCookie(Pac4jConstants.SESSION_ID, expectedSessionId)
      val webContext =
        new Http4sWebContext[IO](request, dispatcher.unsafeRunSync)
      val sessionId = underTest.getSessionId(webContext, false).toScala
      IO(sessionId must beSome(expectedSessionId))
    }
  }

  "getSessionId does not create new session if not asked for it" >> {
    Dispatcher.parallel[IO].use { dispatcher =>
      val sessionRepository = new CacheSessionRepository[IO]
      val request = Request[IO]()
      val underTest =
        new Http4sGenericSessionStore[IO](sessionRepository, dispatcher)()
      val webContext =
        new Http4sWebContext[IO](request, dispatcher.unsafeRunSync)
      val sessionId = underTest.getSessionId(webContext, false).toScala
      IO(sessionId must beNone)
    }
  }

  "getSessionId creates new session and sets according cookie" >> {
    Dispatcher.parallel[IO].use { dispatcher =>
      val sessionRepository = new CacheSessionRepository[IO]
      val underTest =
        new Http4sGenericSessionStore[IO](sessionRepository, dispatcher)()
      val request = Request[IO]()
      val webContext =
        new Http4sWebContext[IO](request, dispatcher.unsafeRunSync)
      val sessionId = underTest.getSessionId(webContext, true).toScala
      val sessionIdKey = Pac4jConstants.SESSION_ID
      IO(
        (sessionId must beSome)
          and (webContext
            .getRequestAttribute(sessionIdKey)
            .toScala must_== (sessionId))
          and (webContext.getResponse.cookies
            .find(_.name == sessionIdKey)
            .map(_.content) must_== sessionId)
      )
    }

    "get from underlying SessionRepository" >> {
      Dispatcher.parallel[IO].use { dispatcher =>
        val sessionId = "sessionId"
        val sessionRepository = new CacheSessionRepository[IO]
        val underTest =
          new Http4sGenericSessionStore[IO](sessionRepository, dispatcher)()
        val request =
          Request[IO]().addCookie(Pac4jConstants.SESSION_ID, sessionId)
        val webContext =
          new Http4sWebContext[IO](request, dispatcher.unsafeRunSync)
        for {
          _ <- sessionRepository.set(sessionId, "key", "value")
          value = underTest.get(webContext, "key").toScala
        } yield value must beSome("value")
      }
    }

    "return None on get for unknown SessionId" >> {
      Dispatcher.parallel[IO].use { dispatcher =>
        val sessionRepository = new CacheSessionRepository[IO]
        val underTest =
          new Http4sGenericSessionStore[IO](sessionRepository, dispatcher)()
        val request = Request[IO]()
        val webContext =
          new Http4sWebContext[IO](request, dispatcher.unsafeRunSync)
        val value = underTest.get(webContext, "key").toScala
        IO(value must beNone)
      }
    }

    "set to underlying SessionRepository" >> {
      Dispatcher.parallel[IO].use { dispatcher =>
        val sessionId = "sessionId"
        val sessionRepository = new CacheSessionRepository[IO]
        val underTest =
          new Http4sGenericSessionStore[IO](sessionRepository, dispatcher)()
        val request =
          Request[IO]().addCookie(Pac4jConstants.SESSION_ID, sessionId)
        val webContext =
          new Http4sWebContext[IO](request, dispatcher.unsafeRunSync)
        underTest.set(webContext, "key", "value")
        for {
          session <- sessionRepository.get(sessionId)
          value = session.getOrElse(Map.empty).get("key")
        } yield value must beSome("value")
      }
    }

    "set `null` removes underlying from SessionRepository" >> {
      Dispatcher.parallel[IO].use { dispatcher =>
        val sessionId = "sessionId"
        val sessionRepository = new CacheSessionRepository[IO]
        val underTest =
          new Http4sGenericSessionStore[IO](sessionRepository, dispatcher)()
        val request =
          Request[IO]().addCookie(Pac4jConstants.SESSION_ID, sessionId)
        val webContext =
          new Http4sWebContext[IO](request, dispatcher.unsafeRunSync)
        for {
          _ <- sessionRepository.set(sessionId, "key", "should vanish")
          _ = underTest.set(webContext, "key", null)
          session <- sessionRepository.get(sessionId)
          value = session.getOrElse(Map.empty).get("key")
        } yield value must beNone
      }
    }

    "destroySession removes session from SessionRepository and Context" >> {
      Dispatcher.parallel[IO].use { dispatcher =>
        val sessionId = "sessionId"
        val sessionRepository = new CacheSessionRepository[IO]
        val underTest =
          new Http4sGenericSessionStore[IO](sessionRepository, dispatcher)()
        val request = Request[IO]()
        val webContext =
          new Http4sWebContext[IO](request, dispatcher.unsafeRunSync)
        webContext.setRequestAttribute(Pac4jConstants.SESSION_ID, sessionId)
        for {
          _ <- sessionRepository.set(sessionId, "key", "should vanish")
          _ = underTest.destroySession(webContext)
          session <- sessionRepository.get(sessionId)
          sessionIdAttribute = webContext
            .getRequestAttribute(Pac4jConstants.SESSION_ID)
            .toScala
          sessionIdCookie = webContext.getRequestCookies.asScala
            .find(_.getName() == Pac4jConstants.SESSION_ID)
        } yield (session must beNone) and (sessionIdAttribute must beNone) and (sessionIdCookie must beNone)
      }
    }

    "renewSession moves data from old session to new one" >> {
      Dispatcher.parallel[IO].use { dispatcher =>
        val oldSessionId = "oldSessionId"
        val sessionRepository = new CacheSessionRepository[IO]
        val underTest =
          new Http4sGenericSessionStore[IO](sessionRepository, dispatcher)()
        val request = Request[IO]()
        val webContext =
          new Http4sWebContext[IO](request, dispatcher.unsafeRunSync)
        webContext.setRequestAttribute(Pac4jConstants.SESSION_ID, oldSessionId)
        for {
          _ <- sessionRepository.set(oldSessionId, "key", "value")
          _ = underTest.renewSession(webContext)
          oldSession <- sessionRepository.get(oldSessionId)
          newSessionId = webContext
            .getRequestAttribute(Pac4jConstants.SESSION_ID)
            .toScala
            .get
            .asInstanceOf[String]
          newSession <- sessionRepository.get(newSessionId)
          newValue = newSession.flatMap(s => s.get("key"))
        } yield (oldSession must beNone) and (newSessionId must be_!=(
          oldSessionId
        )) and (newValue must beSome("value"))
      }
    }

  }
}
