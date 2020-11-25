package org.pac4j.http4s

import cats.implicits._
import cats.effect._
import org.http4s.{Request, Response}
import org.pac4j.core.config.Config
import org.pac4j.core.engine.DefaultCallbackLogic
import org.pac4j.core.http.adapter.HttpActionAdapter

/**
  * Http4s Service to handle callback from after login
  *
  * This is required for web sites where a user logs in, get's redirected to another
  * site to login (e.g. facebook, google etc) and then that site redirects the user
  * back to the original site at a callback handled by _this_ service.
  *
  * @author Iain Cardnell
  */
class CallbackService(config: Config,
                      blocker: Blocker,
                      defaultUrl: Option[String] = None,
                      saveInSession: Boolean = true,
                      multiProfile: Boolean = false,
                      renewSession: Boolean = true,
                      defaultClient: Option[String] = None)
                      (implicit cs: ContextShift[IO]){

  def login(request: Request[IO]): IO[Response[IO]] = {
    val callbackLogic = new DefaultCallbackLogic[IO[Response[IO]], Http4sWebContext]()
    val webContext = Http4sWebContext(request, config)
    blocker.delay[IO, IO[Response[IO]]](callbackLogic.perform(webContext,
      config,
      config.getHttpActionAdapter.asInstanceOf[HttpActionAdapter[IO[Response[IO]], Http4sWebContext]],
      this.defaultUrl.orNull,
      this.saveInSession,
      this.multiProfile,
      this.renewSession,
      this.defaultClient.orNull)).flatten
  }
}
