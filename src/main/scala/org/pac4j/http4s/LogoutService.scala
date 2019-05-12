package org.pac4j.http4s

import org.http4s.dsl._
import org.http4s.{Response, _}
import org.pac4j.core.config.Config
import org.pac4j.core.engine.DefaultLogoutLogic
import org.pac4j.core.http.adapter.HttpActionAdapter
import scalaz.concurrent.Task

/**
  * Http4s Service to handle callback from Id Provider
  */
class LogoutService(config: Config,
                    defaultUrl: String = null,
                    logoutUrlPattern: String = null,
                    localLogout: Boolean = true,
                    destroySession: Boolean = false,
                    centralLogout: Boolean = false) {

  def logout(request: Request): Task[Response] = {
    val logoutLogic = new DefaultLogoutLogic[Response, Http4sWebContext]()
    Ok().map { response =>
      println("!!!! Logout")

      val webContext = new Http4sWebContext(request, response)
      logoutLogic.perform(webContext,
        config,
        config.getHttpActionAdapter.asInstanceOf[HttpActionAdapter[Response, Http4sWebContext]],
        this.defaultUrl,
        this.logoutUrlPattern,
        this.localLogout,
        this.destroySession,
        this.centralLogout)

      webContext.getResponse
    }
  }
}
