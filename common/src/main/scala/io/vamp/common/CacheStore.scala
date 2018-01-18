package io.vamp.common

import com.github.benmanes.caffeine.cache.Caffeine

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scalacache._
import scalacache.caffeine._
import scalacache.modes.sync._

trait CacheStore {
  this: NamespaceProvider ⇒

  protected def maximumSize: Long = 1000L

  private val cache: Cache[Future[Any]] = CaffeineCache(
    Caffeine.newBuilder().maximumSize(maximumSize).build[String, Entry[Future[Any]]]
  )

  def contains(key: String): Boolean = cache.get(key).isDefined

  def getOrPutIfAbsent[T](key: String, put: () ⇒ Future[T])(timeToLivePeriod: FiniteDuration, log: String ⇒ Unit = (_) ⇒ ()): Future[T] = synchronized {
    cache.get(key) match {
      case Some(result) ⇒
        log(s"cache get: $key")
        result.asInstanceOf[Future[T]]
      case None ⇒
        log(s"cache put: $key")
        val value = put()
        cache.put(key)(value, Option(timeToLivePeriod))
        value
    }
  }

  def remove(key: String, log: String ⇒ Unit = (_) ⇒ ()): Unit = synchronized {
    log(s"cache removal: $key")
    cache.remove(key)
  }
}
