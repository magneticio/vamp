package io.vamp.common

import com.github.benmanes.caffeine.cache.Caffeine

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scalacache._
import scalacache.caffeine._
import scalacache.modes.sync._

class CacheStore(maximumSize: Long = 1000L) {

  private val cache: Cache[Future[Any]] = CaffeineCache(
    Caffeine.newBuilder().maximumSize(maximumSize).build[String, Entry[Future[Any]]]
  )

  def contains(key: String): Boolean = cache.get(key).isDefined

  def getOrPutIfAbsent[T](key: String, put: () ⇒ Future[T])(timeToLivePeriod: FiniteDuration, log: String ⇒ Unit = (_) ⇒ ()): Future[T] = synchronized {
    get(key) match {
      case Some(result) ⇒
        log(s"cache get: $key")
        result
      case None ⇒ this.put[T](key, put)(timeToLivePeriod, log)
    }
  }

  def get[T](key: String): Option[Future[T]] = synchronized {
    cache.get(key).map(_.asInstanceOf[Future[T]])
  }

  def put[T](key: String, put: () ⇒ Future[T])(timeToLivePeriod: FiniteDuration, log: String ⇒ Unit = (_) ⇒ ()): Future[T] = synchronized {
    log(s"cache put [${timeToLivePeriod.toSeconds} s]: $key")
    val value = put()
    cache.put(key)(value, Option(timeToLivePeriod))
    value
  }

  def remove(key: String, log: String ⇒ Unit = (_) ⇒ ()): Unit = synchronized {
    log(s"cache removal: $key")
    cache.remove(key)
  }

  def close(): Unit = cache.close()
}
