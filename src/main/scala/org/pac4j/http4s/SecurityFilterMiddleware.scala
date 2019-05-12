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
import scalaz.concurrent.Task

object SecurityFilterMiddleware {

  def securityFilter(config: Config,
                     clients: String = null,
                     authorizers: String = null,
                     matchers: String = null,
                     multiProfile: Boolean = false): HttpMiddleware =
    Middleware { (request, service) =>
      println(s"securityFilter: ${request.uri}")

      Response.notFound(request).flatMap { response =>

        val securityLogic = new DefaultSecurityLogic[Task[Response], Http4sWebContext]
        val context = new Http4sWebContext(request, response)

        // This gets called if user is granted access, proceed to real request
        val securityGrantedAccessAdapter = new SecurityGrantedAccessAdapter[Task[Response], Http4sWebContext] {
          override def adapt(context: Http4sWebContext, profiles: util.Collection[CommonProfile], parameters: AnyRef*): Task[Response] = {
            println("securityGrantedAccessAdapter: " + context.getResponse.orNotFound.toString())
            service(request).map(_.orNotFound)
          }
        }

        val finalResponse = securityLogic.perform(context,
          config,
          securityGrantedAccessAdapter,
          config.getHttpActionAdapter.asInstanceOf[HttpActionAdapter[Task[Response], Http4sWebContext]],
          clients,
          authorizers,
          matchers,
          multiProfile)

        println("securityFilter: final response: " + finalResponse.toString())

        finalResponse
      }
    }
}
