package io.vamp.container_driver.kubernetes

import com.typesafe.scalalogging.Logger
import io.vamp.common.CacheStore
import org.slf4j.LoggerFactory

import scala.util.Try

object K8sCache {
  val job = "Job"
  val pod = "Pod"
  val service = "Service"
  val namespace = "Namespace"
  val daemonSet = "DaemonSet"
  val deployment = "Deployment"
  val replicaSet = "ReplicaSet"
}

class K8sCache(config: K8sCacheConfig) {

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  private lazy val cache = new CacheStore()

  def readRequestWithCache[T](kind: String, request: () ⇒ T): T =
    requestWithCache[T](read = true, kind, request)

  def readRequestWithCache[T](kind: String, name: String, request: () ⇒ T): Option[T] =
    Try(requestWithCache[T](read = true, s"$kind/$name", request)).toOption

  def writeRequestWithCache[T](kind: String, name: String, request: () ⇒ T): T =
    requestWithCache[T](read = false, kind, request)

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
          val ttl = if (read) config.readTimeToLivePeriod else config.writeTimeToLivePeriod
          logger.info(s"cache put [$method ${ttl.toSeconds} s]: $id")
          cache.put(id, Left(response), ttl)
          response
        }
        catch {
          case e: Exception ⇒
            val ttl = config.failureTimeToLivePeriod
            logger.info(s"cache put [$method ${ttl.toSeconds} s]: $id")
            cache.put(id, Right(e), ttl)
            throw e
        }
    }
  }

  def close(): Unit = cache.close()
}
