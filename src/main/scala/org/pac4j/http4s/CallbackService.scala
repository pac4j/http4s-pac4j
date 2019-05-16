package org.pac4j.http4s

import org.http4s.{Request, Response}
import org.pac4j.core.config.Config
import org.pac4j.core.engine.DefaultCallbackLogic
import org.pac4j.core.http.adapter.HttpActionAdapter
import scalaz.concurrent.Task

/**
  * Http4s Service to handle callback from Id Provider
  */
class CallbackService(config: Config,
                      defaultUrl: Option[String] = None,
                      saveInSession: Boolean = true,
                      multiProfile: Boolean = false,
                      renewSession: Boolean = true,
                      defaultClient: Option[String] = None) {

  def login(request: Request): Task[Response] = {
    val callbackLogic = new DefaultCallbackLogic[Task[Response], Http4sWebContext]()
    val webContext = Http4sWebContext(request, config)
    callbackLogic.perform(webContext,
      config,
      config.getHttpActionAdapter.asInstanceOf[HttpActionAdapter[Task[Response], Http4sWebContext]],
      this.defaultUrl.orNull,
      this.saveInSession,
      this.multiProfile,
      this.renewSession,
      this.defaultClient.orNull)
  }
}
