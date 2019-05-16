package org.pac4j.http4s

import org.http4s.{Response, _}
import org.pac4j.core.config.Config
import org.pac4j.core.engine.DefaultLogoutLogic
import org.pac4j.core.http.adapter.HttpActionAdapter
import scalaz.concurrent.Task

/**
  * Http4s Service to handle callback from Id Provider
  */
class LogoutService(config: Config,
                    defaultUrl: Option[String] = None,
                    logoutUrlPattern: Option[String] = None,
                    localLogout: Boolean = true,
                    destroySession: Boolean = false,
                    centralLogout: Boolean = false) {

  def logout(request: Request): Task[Response] = {
    val logoutLogic = new DefaultLogoutLogic[Task[Response], Http4sWebContext]()
    val webContext = Http4sWebContext(request, config)
    logoutLogic.perform(webContext,
      config,
      config.getHttpActionAdapter.asInstanceOf[HttpActionAdapter[Task[Response], Http4sWebContext]],
      this.defaultUrl.orNull,
      this.logoutUrlPattern.orNull,
      this.localLogout,
      this.destroySession,
      this.centralLogout)
  }
}
