package io.vamp.container_driver.kubernetes

import com.typesafe.scalalogging.Logger
import io.kubernetes.client.ApiException
import io.vamp.common.{ CacheStore, Namespace }
import org.slf4j.LoggerFactory

import scala.util.Try

object K8sCache {
  val jobs = "jobs"
  val pods = "pods"
  val nodes = "nodes"
  val services = "services"
  val daemonSets = "daemon-sets"
  val deployments = "deployments"
  val replicaSets = "replica-sets"

  val create = "create"
  val update = "update"
  val delete = "delete"
}

class K8sCache(config: K8sCacheConfig, val namespace: Namespace) {

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  private val cache = new CacheStore()

  logger.info(s"starting Kubernetes cache: ${namespace.name}")

  def readAllWithCache[T](kind: String, selector: String, request: () ⇒ T): T = {
    requestWithCache[T](read = true, "list", id(kind, selector = Option(selector).getOrElse("")), request)._2
  }

  def readWithCache[T](kind: String, name: String, request: () ⇒ T): Option[T] = {
    Try(requestWithCache[T](read = true, "read", id(kind, name), request)).toOption.map(_._2)
  }

  def writeWithCache(operation: String, kind: String, name: String, request: () ⇒ Any): Unit =
    Try(requestWithCache[Any](read = false, operation, id(kind, name, read = false), request)).recover {
      case e: ApiException if e.getCode == 409 ⇒
        logger.warn("K8sCache - Conflict, previous operation still in progress - {}", e.getMessage)
      // conflict, previous operation still in progress
      case e ⇒
        logger.error("K8sCache - error while writing with cache", e)
    }

  private def requestWithCache[T](read: Boolean, operation: String, id: String, request: () ⇒ T): (String, T) = {
    cache.get[(String, Either[T, Exception])](id) match {
      case Some((op, response)) if op == operation ⇒
        logger.debug(s"K8sCache - Getting response from cache for $id on operation $operation")
        response match {
          case Left(r) ⇒ {
            logger.info("K8sCache - Returning from cache for {} on operation {} response was {}", id, operation, response.toString)
            op → r
          }
          case Right(e) ⇒ {
            logger.error(s"K8sCache - Error while retrieving from cache for $id on operation $operation", e)
            throw e
          }
        }
      case _ ⇒
        try {
          logger.info("K8sCache - Sending request for {} on operation {}", id, operation)
          val response = request()
          logger.info("K8sCache - Request sent for {} on operation {} response was {}", id, operation, response.toString)
          val ttl = if (read) config.readTimeToLivePeriod else config.writeTimeToLivePeriod
          logger.info(s"cK8sCache - cache put [${ttl.toSeconds}s]: $id on operation $operation")
          cache.put[(String, Either[T, Exception])](id, operation → Left(response), ttl)
          logger.info("K8sCache - Returning from request for {} on operation {}", id, operation)
          operation → response
        }
        catch {
          case e: Exception ⇒
            logger.error(s"K8sCache - Error while running request for $id on operation $operation", e)
            val ttl = config.failureTimeToLivePeriod
            logger.info(s"cache put [${ttl.toSeconds}s]: $id")
            cache.put[(String, Either[T, Exception])](id, operation → Right(e), ttl)
            throw e
        }
    }
  }

  def invalidate(kind: String, name: String): Unit = {
    val ofKind = all(kind)
    cache.keys.filter(_.startsWith(ofKind)).foreach { key ⇒
      logger.info(s"invalidate cache: $key")
      cache.remove(key)
    }
    (id(kind, name) :: id(kind, name, read = false) :: Nil).foreach { key ⇒
      logger.info(s"invalidate cache: $key")
      cache.remove(key)
    }
  }

  def close(): Unit = {
    logger.info(s"closing Kubernetes cache: ${namespace.name}")
    cache.close()
  }

  private def all(kind: String) = s"r/${namespace.name}/$kind/?"

  private def id(kind: String, name: String = "", selector: String = "", read: Boolean = true) = s"${if (read) "r/" else "w/"}${namespace.name}/$kind/$name?$selector"
}
