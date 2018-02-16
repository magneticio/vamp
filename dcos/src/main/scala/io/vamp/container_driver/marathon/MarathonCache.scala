package io.vamp.container_driver.marathon

import akka.actor.Actor
import io.vamp.common.akka.{ ActorExecutionContextProvider, CommonActorLogging }
import io.vamp.common.{ CacheStore, NamespaceProvider }

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

trait MarathonCache {
  this: Actor with ActorExecutionContextProvider with CommonActorLogging with NamespaceProvider ⇒

  private lazy val readTimeToLivePeriod = MarathonDriverActor.readTimeToLivePeriod()
  private lazy val writeTimeToLivePeriod = MarathonDriverActor.writeTimeToLivePeriod()
  private lazy val failureTimeToLivePeriod = MarathonDriverActor.failureTimeToLivePeriod()

  private lazy val cache = new CacheStore()

  protected def readFromCache[T](id: String, request: () ⇒ Future[T]): Future[T] = {
    getOrPutIfAbsent(r(id), request)(readTimeToLivePeriod)
  }

  protected def writeToCache[T](id: String, request: () ⇒ Future[T]): Future[T] = {
    getOrPutIfAbsent(w(id), request)(writeTimeToLivePeriod)
  }

  protected def inCache(id: String): Boolean = cache.contains(r(id)) || cache.contains(w(id))

  protected def invalidateCache(id: String): Unit = {
    remove(r(id))
    remove(w(id))
  }

  protected def markReadFailure[T](id: String): Unit = markFailure(r(id))

  protected def markWriteFailure[T](id: String): Unit = markFailure(w(id))

  protected def closeCache(): Unit = cache.close()

  private def markFailure[T](id: String): Unit = cache.get[T](id).foreach { value ⇒
    put[T](id, () ⇒ value)(failureTimeToLivePeriod)
  }

  private def getOrPutIfAbsent[T](key: String, put: () ⇒ T)(timeToLivePeriod: FiniteDuration): T = synchronized {
    cache.get[T](key) match {
      case Some(result) ⇒
        log.info(s"cache get: $key")
        result
      case None ⇒ this.put[T](key, put)(timeToLivePeriod)
    }
  }

  private def put[T](key: String, putValue: () ⇒ T)(timeToLivePeriod: FiniteDuration): T = synchronized {
    log.info(s"cache put [${timeToLivePeriod.toSeconds} s]: $key")
    val value = putValue()
    cache.put(key, value, timeToLivePeriod)
    value
  }

  private def remove(key: String): Unit = synchronized {
    log.info(s"cache removal: $key")
    cache.remove(key)
  }

  @inline
  private def r(id: String): String = s"r$id"

  @inline
  private def w(id: String): String = s"w$id"
}
