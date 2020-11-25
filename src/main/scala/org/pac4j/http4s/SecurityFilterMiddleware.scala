package org.pac4j.http4s

import java.util

import cats.implicits._
import cats.effect._
import org.http4s.server.{HttpMiddleware, Middleware}
import org.http4s.{HttpRoutes, Response}

import cats.effect.IO
import org.http4s.server.AuthMiddleware
import org.http4s.{AuthedRoutes, ContextRequest, Response}
import org.http4s.implicits._
import org.pac4j.core.config.Config
import org.pac4j.core.engine.{DefaultSecurityLogic, SecurityGrantedAccessAdapter}
import org.pac4j.core.http.adapter.HttpActionAdapter
import org.pac4j.core.profile.CommonProfile
import cats.data.{Kleisli, OptionT}

/**
  * DefaultSecurityGrantedAccessAdapter gets called if user is granted access
  *
  * It should proceed to real request
  *
  * @param service The http4s route that is being protected
  */
class DefaultSecurityGrantedAccessAdapter(service: AuthedRoutes[util.Collection[CommonProfile], IO])
    extends SecurityGrantedAccessAdapter[IO[Response[IO]], Http4sWebContext] {
  override def adapt(
      context: Http4sWebContext,
      profiles: util.Collection[CommonProfile],
      parameters: AnyRef*
  ): IO[Response[IO]] = {
    service.orNotFound(ContextRequest(profiles, context.getRequest))
  }
}

/**
  * SecurityFilterMiddleware is applied to all routes that need authentication and
  * authorisation.
  *
  * @author Iain Cardnell
  */
object SecurityFilterMiddleware {
  def securityFilter(
      config: Config,
      blocker: Blocker,
      clients: Option[String] = None,
      authorizers: Option[String] = None,
      matchers: Option[String] = None,
      multiProfile: Boolean = false,
      securityGrantedAccessAdapter: AuthedRoutes[util.Collection[CommonProfile], IO] => SecurityGrantedAccessAdapter[
        IO[Response[IO]],
        Http4sWebContext
      ] = new DefaultSecurityGrantedAccessAdapter(_)
  )(implicit cs: ContextShift[IO]): AuthMiddleware[IO, util.Collection[CommonProfile]] =
    service =>
      Kleisli(request => {
        val securityLogic =
          new DefaultSecurityLogic[IO[Response[IO]], Http4sWebContext]
        val context = Http4sWebContext(request, config)
        OptionT.liftF(
          blocker.delay[IO, IO[Response[IO]]](securityLogic.perform(
            context,
            config,
            securityGrantedAccessAdapter(service),
            config.getHttpActionAdapter
              .asInstanceOf[HttpActionAdapter[IO[Response[IO]], Http4sWebContext]],
            clients.orNull,
            authorizers.orNull,
            matchers.orNull,
            multiProfile
          )).flatten
        )
      }
    )
}
