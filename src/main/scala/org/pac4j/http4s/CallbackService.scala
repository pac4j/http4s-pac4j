package org.pac4j.http4s

import org.http4s.{Response, _}
import org.http4s.dsl._
import org.pac4j.core.config.Config
import org.pac4j.core.engine.DefaultCallbackLogic
import org.pac4j.core.http.adapter.HttpActionAdapter
import scalaz.concurrent.Task
import SessionSyntax._
import io.circe.{Json, JsonObject}

/**
  * Http4s Service to handle callback from Id Provider
  */
class CallbackService(config: Config) {
  val defaultUrl: String = null

  val saveInSession: Boolean = true

  val multiProfile: Boolean = false

  val renewSession: Boolean = true

  val defaultClient: String = null

  def login(request: Request): Task[Response] = {
    val callbackLogic = new DefaultCallbackLogic[Response, Http4sWebContext]()
    Ok().map { response =>
      println("!!!! Callback request session:")
      println(request)
      request.session2.foreach(println)
      val responseWithSession = response //if (request.session2.isDefined) response else response.newSession(Json.fromJsonObject(JsonObject.empty))

      val webContext = new Http4sWebContext(request, responseWithSession)
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
