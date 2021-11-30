package org.pac4j.http4s

import cats.syntax.flatMap._
import cats.effect._
import org.http4s._
import org.pac4j.core.config.Config
import org.pac4j.core.engine.DefaultLogoutLogic

/**
  * Http4s Service to handle user logging out from the website
  *
  * @author Iain Cardnell
  */
class LogoutService[F[_]: Sync](config: Config,
    contextBuilder: (Request[F], Config) => Http4sWebContext[F],
    defaultUrl: Option[String] = None,
    logoutUrlPattern: Option[String] = None,
    localLogout: Boolean = true,
    destroySession: Boolean = false,
    centralLogout: Boolean = false
  ) {

  def logout(request: Request[F]): F[Response[F]] = {
    val logoutLogic = new DefaultLogoutLogic()
    val webContext = contextBuilder(request, config)
    Sync[F].blocking(logoutLogic.perform(webContext,
      config.getSessionStore,
      config,
      config.getHttpActionAdapter,
      this.defaultUrl.orNull,
      this.logoutUrlPattern.orNull,
      this.localLogout,
      this.destroySession,
      this.centralLogout).asInstanceOf[F[Response[F]]]).flatten
  }
}
