package org.pac4j.http4s

import org.http4s.{Response, Status}
import org.pac4j.core.context.HttpConstants
import org.pac4j.core.http.adapter.HttpActionAdapter

class DefaultHttpActionAdapter extends HttpActionAdapter[Response, Http4sWebContext] {
  override def adapt(code: Int, context: Http4sWebContext): Response = {
    println(s"requires HTTP action: $code")
    code match {
      case HttpConstants.UNAUTHORIZED => context.response.withStatus(Status.Unauthorized)
      case HttpConstants.FORBIDDEN => context.response.withStatus(Status.Forbidden)
      case HttpConstants.OK => context.response.withStatus(Status.Ok)
      case HttpConstants.NO_CONTENT => context.response.withStatus(Status.NoContent)
      case HttpConstants.TEMP_REDIRECT => context.response
        .withStatus(Status.TemporaryRedirect)
        //.putHeaders(Location.parse(context.get))
        //.redirect (context.getLocation)
    }
  }
}
