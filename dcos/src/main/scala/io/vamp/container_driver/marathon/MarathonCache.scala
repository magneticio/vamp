package io.vamp.container_driver.marathon

import akka.actor.Actor
import io.vamp.common.akka.CommonActorLogging
import io.vamp.common.{ CacheStore, NamespaceProvider }

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait MarathonCache extends CacheStore {
  this: Actor with CommonActorLogging with NamespaceProvider ⇒

  private lazy val readTimeToLivePeriod: FiniteDuration = MarathonDriverActor.readTimeToLivePeriod()
  private lazy val writeTimeToLivePeriod: FiniteDuration = MarathonDriverActor.writeTimeToLivePeriod()

  protected def readCached[T](id: String, request: () ⇒ Future[T]): Future[T] = {
    getOrPutIfAbsent(r(id), request)(readTimeToLivePeriod, log.info)
  }

  protected def writeCached[T](id: String, request: () ⇒ Future[T]): Future[T] = {
    getOrPutIfAbsent(w(id), request)(writeTimeToLivePeriod, log.info)
  }

  protected def inCache(id: String): Boolean = contains(r(id)) || contains(w(id))

  protected def invalidateCache(id: String): Unit = {
    remove(r(id), log.info)
    remove(w(id), log.info)
  }

  @inline
  private def r(id: String): String = s"r$id"

  @inline
  private def w(id: String): String = s"w$id"
}
