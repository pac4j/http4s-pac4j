package org.pac4j.http4s

import cats.syntax.all._
import cats.Applicative
import scala.annotation.tailrec

class CacheSessionRepository[F[_]: Applicative] extends SessionRepository[F] {
  import scala.collection.concurrent.{Map => ConcurrentMap}
  private val cache =
    scala.collection.concurrent.TrieMap[String, Map[String, AnyRef]]()

  override def remove(id: String, key: String): F[Unit] =
    insertOrUpdate(cache)(id)(Map.empty, _ - key).pure[F]

  override def set(id: String, key: String, value: AnyRef): F[Unit] =
    insertOrUpdate(cache)(id)(Map(key -> value), _ + (key -> value)).pure[F]

  override def deleteSession(id: String): F[Boolean] =
    cache.remove(id).isDefined.pure[F]

  override def get(id: String): F[Option[Map[String, AnyRef]]] =
    cache.get(id).pure[F]

  override def update(id: String, session: Map[String, AnyRef]): F[Unit] =
    cache.update(id, session).pure[F]

  private def insertOrUpdate[K, V](
      map: ConcurrentMap[K, V]
  )(key: K)(insert: V, update: V => V): Unit = {
    @tailrec
    def go(): Unit = {
      map.putIfAbsent(key, insert) match {
        case Some(prev) if map.replace(key, prev, update(prev)) => ()
        case Some(_)                                            => go()
        case None                                               => ()
      }
    }
    go()
  }
}
