package org.pac4j.http4s

import org.http4s.{Response, Status}
import org.pac4j.core.context.HttpConstants
import org.pac4j.core.http.adapter.HttpActionAdapter
import scalaz.concurrent.Task

class DefaultHttpActionAdapter extends HttpActionAdapter[Task[Response], Http4sWebContext] {
  override def adapt(code: Int, context: Http4sWebContext): Task[Response] = {
    println(s"requires HTTP action: $code")
    Task.delay {
      code match {
        case HttpConstants.UNAUTHORIZED => context.getResponse.withStatus(Status.Unauthorized)
        case HttpConstants.FORBIDDEN => context.getResponse.withStatus(Status.Forbidden)
        case HttpConstants.OK => context.getResponse.withStatus(Status.Ok)
        case HttpConstants.NO_CONTENT => context.getResponse.withStatus(Status.NoContent)
        case HttpConstants.TEMP_REDIRECT => context.getResponse.withStatus(Status.TemporaryRedirect)
      }
    }
  }
}
