package io.vamp.common

import com.github.benmanes.caffeine.cache.Caffeine

import scala.concurrent.duration.FiniteDuration
import scalacache._
import scalacache.caffeine._
import scalacache.modes.sync._

class CacheStore(maximumSize: Long = 10000L) {

  private val store: Cache[Any] = CaffeineCache(
    Caffeine.newBuilder().maximumSize(maximumSize).build[String, Entry[Any]]
  )

  def contains(key: String): Boolean = store.get(key).isDefined

  def get[T](key: String): Option[T] = store.get(key).map(_.asInstanceOf[T])

  def put[T](key: String, value: T, timeToLivePeriod: FiniteDuration): Unit = store.put(key)(value, Option(timeToLivePeriod))

  def remove(key: String): Unit = store.remove(key)

  def close(): Unit = store.close()
}
