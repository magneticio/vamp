package io.vamp.container_driver

import akka.event.LoggingAdapter
import com.github.benmanes.caffeine.cache.Caffeine
import io.vamp.common.NamespaceProvider

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scalacache._
import scalacache.caffeine._
import scalacache.modes.sync._

trait ContainerDriverCache {
  this: NamespaceProvider ⇒

  def log: LoggingAdapter

  private val cache: Cache[Future[Any]] = CaffeineCache(Caffeine.newBuilder().build[String, Entry[Future[Any]]])

  def getOrPutIfAbsent[T](id: String, put: () ⇒ Future[T])(timeToLivePeriod: FiniteDuration): Future[T] = {
    cache.get(id) match {
      case Some(result) ⇒
        log.info(s"cache get: $id")
        result.asInstanceOf[Future[T]]
      case None ⇒
        log.info(s"cache put: $id")
        val value = put()
        cache.put(id)(value, Option(timeToLivePeriod))
        value
    }
  }
}
