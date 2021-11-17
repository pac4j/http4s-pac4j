package org.pac4j.http4s

import java.util
import cats.effect._
import cats.syntax.flatMap._
import org.http4s.{AuthedRoutes, ContextRequest, Request, Response}
import org.http4s.server.AuthMiddleware
import org.http4s.implicits._
import org.pac4j.core.config.Config
import org.pac4j.core.engine.{DefaultSecurityLogic, SecurityGrantedAccessAdapter}
import org.pac4j.core.profile.{CommonProfile, UserProfile}
import cats.data.{Kleisli, OptionT}
import org.pac4j.core.context.WebContext
import org.pac4j.core.context.session.SessionStore
import org.pac4j.http4s

import scala.collection.JavaConverters._

/**
  * DefaultSecurityGrantedAccessAdapter gets called if user is granted access
  *
  * It should proceed to real request
  *
  * @param service The http4s route that is being protected
  */
class DefaultSecurityGrantedAccessAdapter[F[_] <: AnyRef : Sync](service: AuthedRoutes[List[CommonProfile], F])
    extends SecurityGrantedAccessAdapter {

  override def adapt(context: WebContext, sessionStore: SessionStore, profiles: util.Collection[UserProfile], parameters: AnyRef*): AnyRef =
    service.orNotFound(
      ContextRequest[F, List[CommonProfile]](profiles.asScala.toList.map(_.asInstanceOf[CommonProfile]), context.asInstanceOf[http4s.Http4sWebContext[F]].getRequest)
    )
}

/**
  * SecurityFilterMiddleware is applied to all routes that need authentication and
  * authorisation.
  *
  * @author Iain Cardnell
  */
object SecurityFilterMiddleware {

  def defaultSecurityGrantedAccessAdapter[F[_] <: AnyRef : Sync]: AuthedRoutes[List[CommonProfile], F] => SecurityGrantedAccessAdapter =
    (a: AuthedRoutes[List[CommonProfile], F]) => new DefaultSecurityGrantedAccessAdapter[F](a)

  def securityFilter[F[_] <: AnyRef : Sync](
      config: Config,
      blocker: Blocker,
      contextBuilder: (Request[F], Config) => Http4sWebContext[F],
      clients: Option[String] = None,
      authorizers: Option[String] = None,
      matchers: Option[String] = None
  )(implicit cs: ContextShift[F]): AuthMiddleware[F, List[CommonProfile]] =
    securityFilter(config, blocker, contextBuilder, clients, authorizers, matchers, defaultSecurityGrantedAccessAdapter[F])

  def securityFilter[F[_] : Sync](
      config: Config,
      blocker: Blocker,
      contextBuilder: (Request[F], Config) => Http4sWebContext[F],
      clients: Option[String],
      authorizers: Option[String],
      matchers: Option[String],
      securityGrantedAccessAdapter: AuthedRoutes[List[CommonProfile], F] => SecurityGrantedAccessAdapter
  )(implicit cs: ContextShift[F]): AuthMiddleware[F, List[CommonProfile]] =
    service =>
      Kleisli(request => {
        val securityLogic =
          new DefaultSecurityLogic
        val context = contextBuilder(request, config)
        OptionT.liftF(
          blocker.delay[F, F[Response[F]]](securityLogic.perform(
            context,
            config.getSessionStore,
            config,
            securityGrantedAccessAdapter(service),
            config.getHttpActionAdapter,
            clients.orNull,
            authorizers.orNull,
            matchers.orNull
          ).asInstanceOf[F[Response[F]]]).flatten
        )
      }
    )
}
