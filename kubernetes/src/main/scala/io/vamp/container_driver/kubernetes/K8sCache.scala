package io.vamp.container_driver.kubernetes

import com.google.gson.JsonSyntaxException
import com.typesafe.scalalogging.Logger
import io.vamp.common.{ CacheStore, Namespace }
import org.slf4j.LoggerFactory

import scala.util.Try

object K8sCache {
  val jobs = "jobs"
  val pods = "pods"
  val services = "services"
  val daemonSets = "daemon-sets"
  val deployments = "deployments"
  val replicaSets = "replica-sets"
}

class K8sCache(config: K8sCacheConfig, val namespace: Namespace) {

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  private val cache = new CacheStore()

  logger.info(s"starting Kubernetes cache: ${namespace.name}")

  def readRequestWithCache[T](kind: String, request: () ⇒ T): T =
    requestWithCache[T](read = true, id(kind), request)

  def readRequestWithCache[T](kind: String, name: String, request: () ⇒ T): Option[T] =
    Try(requestWithCache[T](read = true, id(kind, name), request)).toOption

  def writeRequestWithCache(kind: String, name: String, request: () ⇒ Any): Unit =
    Try(requestWithCache[Any](read = false, id(kind, name, read = false), request)).recover {
      case _: JsonSyntaxException ⇒
      case e                      ⇒ logger.warn(e.getMessage)
    }

  private def requestWithCache[T](read: Boolean, id: String, request: () ⇒ T): T = {
    cache.get[Either[T, Exception]](id) match {
      case Some(response) ⇒
        logger.info(s"cache get: $id")
        response match {
          case Left(r)  ⇒ r
          case Right(e) ⇒ throw e
        }
      case None ⇒
        try {
          val response = request()
          val ttl = if (read) config.readTimeToLivePeriod else config.writeTimeToLivePeriod
          logger.info(s"cache put [${ttl.toSeconds}s]: $id")
          cache.put(id, Left(response), ttl)
          response
        }
        catch {
          case e: Exception ⇒
            val ttl = config.failureTimeToLivePeriod
            logger.info(s"cache put [${ttl.toSeconds}s]: $id")
            cache.put(id, Right(e), ttl)
            throw e
        }
    }
  }

  def invalidate(kind: String, name: String): Unit = {
    if (cache.contains(id(kind, name)) || cache.contains(id(kind, name, read = false))) {
      logger.info(s"invalidate cache: ${namespace.name}/$kind/")
      cache.remove(id(kind))

      logger.info(s"invalidate cache: ${namespace.name}/$kind/$name")
      cache.remove(id(kind, name))
      cache.remove(id(kind, name, read = false))
    }
  }

  def close(): Unit = {
    logger.info(s"closing Kubernetes cache: ${namespace.name}")
    cache.close()
  }

  private def id(kind: String, name: String = "", read: Boolean = true) = s"${if (read) "r/" else "w/"}${namespace.name}/$kind/$name"
}
