package org.pac4j.http4s

/** Repository trait for customizing Http4sGenericSessionStore.
  */
trait SessionRepository[F[_]] {
  def get(id: String): F[Option[Map[String, AnyRef]]]
  def set(id: String, key: String, value: AnyRef): F[Unit]
  def remove(id: String, key: String): F[Unit]
  def update(id: String, session: Map[String, AnyRef]): F[Unit]
  def deleteSession(id: String): F[Boolean]
}
