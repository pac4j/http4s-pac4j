package org.pac4j.http4s

import cats.implicits._
import cats.effect._
import org.http4s.{Response, _}
import org.pac4j.core.config.Config
import org.pac4j.core.engine.DefaultLogoutLogic
import org.pac4j.core.http.adapter.HttpActionAdapter

/**
  * Http4s Service to handle user logging out from the website
  *
  * @author Iain Cardnell
  */
class LogoutService(config: Config,
                    blocker: Blocker,
                    defaultUrl: Option[String] = None,
                    logoutUrlPattern: Option[String] = None,
                    localLogout: Boolean = true,
                    destroySession: Boolean = false,
                    centralLogout: Boolean = false)
                   (implicit cs: ContextShift[IO]) {

  def logout(request: Request[IO]): IO[Response[IO]] = {
    val logoutLogic = new DefaultLogoutLogic[IO[Response[IO]], Http4sWebContext]()
    val webContext = Http4sWebContext(request, config)
    blocker.delay[IO, IO[Response[IO]]](logoutLogic.perform(webContext,
      config,
      config.getHttpActionAdapter.asInstanceOf[HttpActionAdapter[IO[Response[IO]], Http4sWebContext]],
      this.defaultUrl.orNull,
      this.logoutUrlPattern.orNull,
      this.localLogout,
      this.destroySession,
      this.centralLogout)).flatten
  }
}
