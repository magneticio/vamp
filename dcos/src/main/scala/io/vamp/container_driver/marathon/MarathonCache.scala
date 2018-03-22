package io.vamp.container_driver.marathon

import akka.event.LoggingAdapter
import io.vamp.common.CacheStore

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class MarathonCache(config: MarathonCacheConfig) {

  private lazy val cache = new CacheStore()

  def read[T](id: String, request: () ⇒ Future[T])(implicit logger: LoggingAdapter): Future[T] = {
    getOrPutIfAbsent("read", r(id), request)(config.readTimeToLivePeriod)
  }

  def write[T](operation: String, id: String, request: () ⇒ Future[T])(implicit logger: LoggingAdapter): Future[T] = {
    getOrPutIfAbsent(operation, w(id), request)(config.writeTimeToLivePeriod)
  }

  def readFailure[T](id: String)(implicit logger: LoggingAdapter): Unit = markFailure("read", r(id))

  def writeFailure[T](operation: String, id: String)(implicit logger: LoggingAdapter): Unit = markFailure(operation, w(id))

  def inCache(id: String): Boolean = cache.contains(r(id)) || cache.contains(w(id))

  def invalidate(id: String)(implicit logger: LoggingAdapter): Unit = {
    remove(r(id))
    remove(w(id))
  }

  def close(): Unit = cache.close()

  private def getOrPutIfAbsent[T](operation: String, key: String, put: () ⇒ T)(timeToLivePeriod: FiniteDuration)(implicit logger: LoggingAdapter): T = synchronized {
    get[T](key) match {
      case Some(result) if operation == result._1 ⇒
        logger.debug(s"cache get: $key")
        result._2
      case _ ⇒ this.put[T](operation, key, put)(timeToLivePeriod)._2
    }
  }

  private def markFailure[T](operation: String, id: String)(implicit logger: LoggingAdapter): Unit = get[T](id).foreach { value ⇒
    put[T](operation, id, () ⇒ value._2)(config.failureTimeToLivePeriod)
  }

  private def get[T](id: String): Option[(String, T)] = cache.get[(String, T)](id)

  private def put[T](operation: String, key: String, putValue: () ⇒ T)(timeToLivePeriod: FiniteDuration)(implicit logger: LoggingAdapter): (String, T) = synchronized {
    logger.info(s"cache put [${timeToLivePeriod.toSeconds} s]: $key")
    val value = operation → putValue()
    cache.put[(String, T)](key, value, timeToLivePeriod)
    value
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
