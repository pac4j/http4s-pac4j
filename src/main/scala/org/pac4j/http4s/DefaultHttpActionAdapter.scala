package org.pac4j.http4s

import cats.effect.IO
import org.http4s.{Response, Status}
import org.pac4j.core.context.HttpConstants
import org.pac4j.core.http.adapter.HttpActionAdapter

/**
  * DefaultHttpActionAdapter sets the correct status codes on the response.
  *
  * @author Iain Cardnell
  */
object DefaultHttpActionAdapter extends HttpActionAdapter[IO[Response[IO]], Http4sWebContext] {
  override def adapt(code: Int, context: Http4sWebContext): IO[Response[IO]] = {
    IO.delay {
      code match {
        case HttpConstants.UNAUTHORIZED => context.setResponseStatus(Status.Unauthorized.code)
        case HttpConstants.FORBIDDEN => context.setResponseStatus(Status.Forbidden.code)
        case HttpConstants.OK => context.setResponseStatus(Status.Ok.code)
        case HttpConstants.NO_CONTENT => context.setResponseStatus(Status.NoContent.code)
        case HttpConstants.TEMP_REDIRECT => context.setResponseStatus(Status.Found.code)
      }
      context.getResponse
    }
  }
}
