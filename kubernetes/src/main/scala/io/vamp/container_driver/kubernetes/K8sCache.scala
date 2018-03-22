package io.vamp.container_driver.kubernetes

import com.typesafe.scalalogging.Logger
import io.kubernetes.client.ApiException
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
      case e: ApiException if e.getCode == 409 ⇒ // conflict, previous operation still in progress
      case e                                   ⇒ logger.warn(e.getMessage)
    }

  private def requestWithCache[T](read: Boolean, operation: String, id: String, request: () ⇒ T): (String, T) = {
    cache.get[(String, Either[T, Exception])](id) match {
      case Some((op, response)) if op == operation ⇒
        logger.debug(s"cache get: $id")
        response match {
          case Left(r)  ⇒ op → r
          case Right(e) ⇒ throw e
        }
      case _ ⇒
        try {
          val response = request()
          val ttl = if (read) config.readTimeToLivePeriod else config.writeTimeToLivePeriod
          logger.info(s"cache put [${ttl.toSeconds}s]: $id")
          cache.put[(String, Either[T, Exception])](id, operation → Left(response), ttl)
          operation → response
        }
        catch {
          case e: Exception ⇒
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
