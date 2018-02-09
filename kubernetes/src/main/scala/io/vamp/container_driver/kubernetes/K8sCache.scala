package io.vamp.container_driver.kubernetes

import com.typesafe.scalalogging.Logger
import io.vamp.common.CacheStore
import org.slf4j.LoggerFactory

import scala.util.Try

object K8sCache {
  val job = "job"
  val pod = "pod"
  val service = "service"
  val namespace = "namespace"
  val daemonSet = "daemon-set"
  val deployment = "deployment"
  val replicaSet = "replica-set"
}

class K8sCache(config: K8sConfig) {

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  private val cache = new CacheStore()

  logger.info(s"starting Kubernetes cache: ${config.url}")

  def readRequestWithCache[T](kind: String, request: () ⇒ T): T =
    requestWithCache[T](read = true, id(kind), request)

  def readRequestWithCache[T](kind: String, name: String, request: () ⇒ T): Option[T] =
    Try(requestWithCache[T](read = true, id(kind, name), request)).toOption

  def writeRequestWithCache[T](kind: String, name: String, request: () ⇒ T): T =
    requestWithCache[T](read = false, id(kind, name, read = false), request)

  private def requestWithCache[T](read: Boolean, id: String, request: () ⇒ T): T = {
    val method = if (read) "read" else "write"
    cache.get[Either[T, Exception]](id) match {
      case Some(response) ⇒
        logger.info(s"cache get [$method]: $id")
        response match {
          case Left(r)  ⇒ r
          case Right(e) ⇒ throw e
        }
      case None ⇒
        try {
          val response = request()
          val ttl = if (read) config.cache.readTimeToLivePeriod else config.cache.writeTimeToLivePeriod
          logger.info(s"cache put [$method ${ttl.toSeconds} s]: $id")
          cache.put(id, Left(response), ttl)
          response
        }
        catch {
          case e: Exception ⇒
            val ttl = config.cache.failureTimeToLivePeriod
            logger.info(s"cache put [$method ${ttl.toSeconds} s]: $id")
            cache.put(id, Right(e), ttl)
            throw e
        }
    }
  }

  def invalidate(kind: String, name: String): Unit = {
    cache.remove(id(kind))
    cache.remove(id(kind, name))
    cache.remove(id(kind, name, read = false))
  }

  def close(): Unit = {
    logger.info(s"closing Kubernetes cache: ${config.url}")
    cache.close()
  }

  private def id(kind: String, name: String = "", read: Boolean = true) = s"${if (read) "r/" else "w/"}$kind/$name"
}
