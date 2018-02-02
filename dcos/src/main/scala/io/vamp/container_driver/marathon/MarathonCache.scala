package io.vamp.container_driver.marathon

import akka.actor.Actor
import io.vamp.common.akka.{ ActorExecutionContextProvider, CommonActorLogging }
import io.vamp.common.{ CacheStore, NamespaceProvider }

import scala.concurrent.Future

trait MarathonCache {
  this: Actor with ActorExecutionContextProvider with CommonActorLogging with NamespaceProvider ⇒

  private lazy val readTimeToLivePeriod = MarathonDriverActor.readTimeToLivePeriod()
  private lazy val writeTimeToLivePeriod = MarathonDriverActor.writeTimeToLivePeriod()
  private lazy val failureTimeToLivePeriod = MarathonDriverActor.failureTimeToLivePeriod()

  private lazy val cache = new CacheStore()

  protected def readFromCache[T](id: String, request: () ⇒ Future[T]): Future[T] = {
    cache.getOrPutIfAbsent(r(id), request)(readTimeToLivePeriod, log.info)
  }

  protected def writeToCache[T](id: String, request: () ⇒ Future[T]): Future[T] = {
    cache.getOrPutIfAbsent(w(id), request)(writeTimeToLivePeriod, log.info)
  }

  protected def inCache(id: String): Boolean = cache.contains(r(id)) || cache.contains(w(id))

  protected def invalidateCache(id: String): Unit = {
    cache.remove(r(id), log.info)
    cache.remove(w(id), log.info)
  }

  protected def markReadFailure[T](id: String): Unit = markFailure(r(id))

  protected def markWriteFailure[T](id: String): Unit = markFailure(w(id))

  protected def closeCache(): Unit = cache.close()

  private def markFailure[T](id: String): Unit = cache.get(id).foreach { value ⇒
    cache.put(id, () ⇒ value)(failureTimeToLivePeriod, log.info)
  }

  @inline
  private def r(id: String): String = s"r$id"

  @inline
  private def w(id: String): String = s"w$id"
}
