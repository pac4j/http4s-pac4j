package org.pac4j.http4s

import org.http4s.{Response, _}
import org.http4s.dsl._
import org.pac4j.core.config.Config
import org.pac4j.core.engine.DefaultCallbackLogic
import org.pac4j.core.http.adapter.HttpActionAdapter
import scalaz.concurrent.Task


/**
  * Http4s Service to handle callback from Id Provider
  */
class CallbackService(config: Config) {
  val defaultUrl: String = null

  val saveInSession: Boolean = false

  val multiProfile: Boolean = false

  val renewSession: Boolean = false

  val defaultClient: String = null

  def login(request: Request): Task[Response] = {
    val callbackLogic = new DefaultCallbackLogic[Response, Http4sWebContext]()
    Ok().map { resp =>
      val webContext = new Http4sWebContext(request, resp)
      callbackLogic.perform(webContext,
        config,
        config.getHttpActionAdapter.asInstanceOf[HttpActionAdapter[Response, Http4sWebContext]],
        this.defaultUrl,
        this.saveInSession,
        this.multiProfile,
        this.renewSession,
        this.defaultClient)
      webContext.response
    }
  }
}
