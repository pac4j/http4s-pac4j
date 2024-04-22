package org.pac4j

import io.circe.Json
import org.http4s.Request

package object http4s {
  /*
   * Session objects are just Json
   */
  type Session = Json

  type Http4sContextBuilder[F[_]] = Request[F] => Http4sWebContext[F]
}
