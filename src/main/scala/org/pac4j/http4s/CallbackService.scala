package org.pac4j.http4s

import cats.syntax.flatMap._
import cats.effect._
import org.http4s.{Request, Response}
import org.pac4j.core.config.Config
import org.pac4j.core.engine.DefaultCallbackLogic

/**
  * Http4s Service to handle callback from after login
  *
  * This is required for web sites where a user logs in, get's redirected to another
  * site to login (e.g. facebook, google etc) and then that site redirects the user
  * back to the original site at a callback handled by _this_ service.
  *
  * @author Iain Cardnell
  */
class CallbackService[F[_]: Sync](config: Config,
    contextBuilder: (Request[F], Config) => Http4sWebContext[F],
    defaultUrl: Option[String] = None,
    renewSession: Boolean = true,
    defaultClient: Option[String] = None
  ) {

  def callback(request: Request[F]): F[Response[F]] = {
    val callbackLogic = new DefaultCallbackLogic()
    val webContext = contextBuilder(request, config)
    Sync[F].blocking(callbackLogic.perform(webContext,
      config.getSessionStoreFactory.newSessionStore(),
      config,
      config.getHttpActionAdapter,
      this.defaultUrl.orNull,
      this.renewSession,
      this.defaultClient.orNull).asInstanceOf[F[Response[F]]]).flatten
  }
}
