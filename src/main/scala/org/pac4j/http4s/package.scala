package org.pac4j

import io.circe.Json

package object http4s {
  /*
   * Session objects are just Json
   */
  type Session = Json
}
