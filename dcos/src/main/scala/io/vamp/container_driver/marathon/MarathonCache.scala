package io.vamp.container_driver.marathon

import akka.event.LoggingAdapter
import io.vamp.common.CacheStore

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class MarathonCache(config: MarathonCacheConfig) {

  private lazy val cache = new CacheStore()

  def read[T](id: String, request: () ⇒ Future[T])(implicit logger: LoggingAdapter): Future[T] = {
    getOrPutIfAbsent(r(id), request)(config.readTimeToLivePeriod)
  }

  def write[T](id: String, request: () ⇒ Future[T])(implicit logger: LoggingAdapter): Future[T] = {
    getOrPutIfAbsent(w(id), request)(config.writeTimeToLivePeriod)
  }

  def readFailure[T](id: String)(implicit logger: LoggingAdapter): Unit = markFailure(r(id))

  def writeFailure[T](id: String)(implicit logger: LoggingAdapter): Unit = markFailure(w(id))

  def inCache(id: String): Boolean = cache.contains(r(id)) || cache.contains(w(id))

  def invalidate(id: String)(implicit logger: LoggingAdapter): Unit = {
    remove(r(id))
    remove(w(id))
  }

  def close(): Unit = cache.close()

  private def getOrPutIfAbsent[T](key: String, put: () ⇒ T)(timeToLivePeriod: FiniteDuration)(implicit logger: LoggingAdapter): T = synchronized {
    cache.get[T](key) match {
      case Some(result) ⇒
        logger.debug(s"cache get: $key")
        result
      case None ⇒ this.put[T](key, put)(timeToLivePeriod)
    }
  }

  private def put[T](key: String, putValue: () ⇒ T)(timeToLivePeriod: FiniteDuration)(implicit logger: LoggingAdapter): T = synchronized {
    logger.info(s"cache put [${timeToLivePeriod.toSeconds} s]: $key")
    val value = putValue()
    cache.put(key, value, timeToLivePeriod)
    value
  }

  private def markFailure[T](id: String)(implicit logger: LoggingAdapter): Unit = cache.get[T](id).foreach { value ⇒
    put[T](id, () ⇒ value)(config.failureTimeToLivePeriod)
  }

  private def remove(key: String)(implicit logger: LoggingAdapter): Unit = synchronized {
    logger.info(s"cache remove: $key")
    cache.remove(key)
  }

  @inline
  private def r(id: String): String = s"r$id"

  @inline
  private def w(id: String): String = s"w$id"
}
