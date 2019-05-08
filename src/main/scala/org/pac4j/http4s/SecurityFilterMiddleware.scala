package org.pac4j.http4s

import java.util

import org.http4s.server.{HttpMiddleware, Middleware}
import org.http4s.{Pass, Status}
import org.pac4j.core.config.Config
import org.pac4j.core.engine.{DefaultSecurityLogic, SecurityGrantedAccessAdapter}
import org.pac4j.core.http.adapter.HttpActionAdapter
import org.pac4j.core.profile.CommonProfile

object SecurityFilterMiddleware {
  val SECURITY_GRANTED_ACCESS = "SECURITY_GRANTED_ACCESS"

  private val clients: String = null

  private val authorizers: String = null

  private val matchers: String = null

  private val multiProfile: Boolean = false

  def securityFilter(config: Config): HttpMiddleware =
    Middleware { (request, service) =>
      // Is this wrong? executing the service before we have decided on the authorization?!
      service(request).map { response =>
        val securityLogic = new DefaultSecurityLogic[String, Http4sWebContext]
        val context = new Http4sWebContext(request, response.orNotFound) //, config.getSessionStore)

        val securityGrantedAccessAdapter = new SecurityGrantedAccessAdapter[String, Http4sWebContext] {
          override def adapt(context: Http4sWebContext, profiles: util.Collection[CommonProfile], parameters: AnyRef*): String = SECURITY_GRANTED_ACCESS
        }
        val result = securityLogic.perform(context, config, securityGrantedAccessAdapter,
          config.getHttpActionAdapter.asInstanceOf[HttpActionAdapter[String, Http4sWebContext]],
          this.clients, this.authorizers, this.matchers, this.multiProfile)

        if (result eq SECURITY_GRANTED_ACCESS) { // It means that the access is granted: continue
          println("Received SECURITY_GRANTED_ACCESS -> continue")
          response
        } else {
          println("Halt the request processing")
          // stop the processing if no SECURITY_GRANTED_ACCESS has been received
          response.cata(r => r.withStatus(Status.Unauthorized), Pass)
        }
      }
    }
}
