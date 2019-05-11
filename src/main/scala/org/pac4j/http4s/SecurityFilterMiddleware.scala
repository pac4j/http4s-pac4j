package org.pac4j.http4s

import java.util

import io.circe.{Json, JsonObject}
import org.http4s.server.{HttpMiddleware, Middleware}
import org.http4s.{Pass, Response, Status}
import org.pac4j.core.config.Config
import org.pac4j.core.engine.{DefaultSecurityLogic, SecurityGrantedAccessAdapter}
import org.pac4j.core.http.adapter.HttpActionAdapter
import org.pac4j.core.profile.CommonProfile
import SessionSyntax._

object SecurityFilterMiddleware {
  private val clients: String = null

  private val authorizers: String = null

  private val matchers: String = null

  private val multiProfile: Boolean = false

  def securityFilter(config: Config): HttpMiddleware =
    Middleware { (request, service) =>
      println(s"securityFilter: ${request.uri}")

      // Is this wrong? executing the service before we have decided on the authorization?!
      Response.notFound(request).map { response =>
        println("securityFilter: initial response: " + response.toString())
        val responseWithSession = response.orNotFound
        println("securityFilter: responseWithSession: " + responseWithSession.toString())

        val securityLogic = new DefaultSecurityLogic[Response, Http4sWebContext]
        val context = new Http4sWebContext(request, responseWithSession) //, config.getSessionStore)

        // This gets called if user is granted access, proceed to real request
        val securityGrantedAccessAdapter = new SecurityGrantedAccessAdapter[Response, Http4sWebContext] {
          override def adapt(context: Http4sWebContext, profiles: util.Collection[CommonProfile], parameters: AnyRef*): Response = {
            println("securityGrantedAccessAdapter: " + context.getResponse.orNotFound.toString())
            service(request)
          }
        }

        val finalResponse = securityLogic.perform(context,
          config,
          securityGrantedAccessAdapter,
          config.getHttpActionAdapter.asInstanceOf[HttpActionAdapter[Response, Http4sWebContext]],
          this.clients,
          this.authorizers,
          this.matchers,
          this.multiProfile)

        println("securityFilter: final response: " + finalResponse.toString())

        finalResponse
      }
    }
}
