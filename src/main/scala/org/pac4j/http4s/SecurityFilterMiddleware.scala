package org.pac4j.http4s

import java.util

import org.http4s.server.{HttpMiddleware, Middleware}
import org.http4s.{HttpService, Response}
import org.pac4j.core.config.Config
import org.pac4j.core.engine.{DefaultSecurityLogic, SecurityGrantedAccessAdapter}
import org.pac4j.core.http.adapter.HttpActionAdapter
import org.pac4j.core.profile.CommonProfile
import scalaz.concurrent.Task


/**
  * DefaultSecurityGrantedAccessAdapter gets called if user is granted access
  *
  * It should proceed to real request
  *
  * @param service The http4s route that is being protected
  */
class DefaultSecurityGrantedAccessAdapter(service: HttpService) extends SecurityGrantedAccessAdapter[Task[Response], Http4sWebContext] {
  override def adapt(context: Http4sWebContext, profiles: util.Collection[CommonProfile], parameters: AnyRef*): Task[Response] = {
    service(context.getRequest).map(_.orNotFound)
  }
}

/**
  * SecurityFilterMiddleware is applied to all routes that need authentication and
  * authorisation.
  *
  * @author Iain Cardnell
  */
object SecurityFilterMiddleware {

  def securityFilter(config: Config,
                     clients: Option[String] = None,
                     authorizers: Option[String] = None,
                     matchers: Option[String] = None,
                     multiProfile: Boolean = false,
                     securityGrantedAccessAdapter: HttpService => SecurityGrantedAccessAdapter[Task[Response], Http4sWebContext] = new DefaultSecurityGrantedAccessAdapter(_)): HttpMiddleware =
    Middleware { (request, service) =>
      val securityLogic = new DefaultSecurityLogic[Task[Response], Http4sWebContext]
      val context = Http4sWebContext(request, config)
      securityLogic.perform(context,
        config,
        securityGrantedAccessAdapter(service),
        config.getHttpActionAdapter.asInstanceOf[HttpActionAdapter[Task[Response], Http4sWebContext]],
        clients.orNull,
        authorizers.orNull,
        matchers.orNull,
        multiProfile)
    }
}
