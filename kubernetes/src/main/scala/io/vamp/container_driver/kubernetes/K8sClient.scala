package io.vamp.container_driver.kubernetes

import java.io.FileInputStream
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.typesafe.scalalogging.Logger
import io.kubernetes.client.ApiClient
import io.kubernetes.client.apis.{ ApisApi, BatchV1Api, CoreV1Api, ExtensionsV1beta1Api }
import io.vamp.common.Namespace
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.io.Source
import scala.util.Try

private case class SharedK8sClient(client: K8sClient, counter: Int)

object K8sClient {

  private val logger = Logger(LoggerFactory.getLogger(getClass))

  private val clients = new mutable.HashMap[K8sClientConfig, SharedK8sClient]()

  def acquire(config: K8sClientConfig)(implicit namespace: Namespace, system: ActorSystem): K8sClient = synchronized {
    logger.info(s"acquiring Kubernetes connection: ${config.url}")
    val client = clients.get(config) match {
      case Some(shared) ⇒
        clients.put(config, shared.copy(counter = shared.counter + 1))
        shared.client
      case None ⇒
        logger.info(s"creating new Kubernetes connection: ${config.url}")
        val shared = SharedK8sClient(new K8sClient(config), 1)
        clients.put(config, shared)
        shared.client
    }
    client.acquire()
    client
  }

  def release(config: K8sClientConfig)(implicit namespace: Namespace): Unit = synchronized {
    logger.info(s"releasing Kubernetes connection: ${config.url}")
    clients.get(config) match {
      case Some(shared) if shared.counter == 1 ⇒
        logger.info(s"closing Kubernetes connection: ${config.url}")
        clients.remove(config)
        shared.client.close()
      case Some(shared) ⇒
        shared.client.release()
        clients.put(config, shared.copy(counter = shared.counter - 1))
      case None ⇒
    }
  }
}

class K8sClient(val config: K8sClientConfig)(implicit system: ActorSystem) {

  private val api: ApiClient = {
    val client = new ApiClient()
    client.setBasePath(config.url)
    client.getHttpClient.setReadTimeout(0, TimeUnit.SECONDS)
    val apiKey = if (config.bearer.nonEmpty) config.bearer else Try(Source.fromFile(config.token).mkString).getOrElse("")
    if (apiKey.nonEmpty) client.setApiKey(s"Bearer $apiKey")
    if (config.username.nonEmpty) client.setUsername(config.username)
    if (config.password.nonEmpty) client.setPassword(config.password)
    if (config.serverCaCert.nonEmpty) client.setSslCaCert(new FileInputStream(config.serverCaCert))
    client.setVerifyingSsl(config.tlsCheck)
  }

  val watch = new K8sWatch(this)

  val caches = new mutable.HashSet[K8sCache]()

  lazy val apisApi: ApisApi = new ApisApi(api)

  lazy val coreV1Api: CoreV1Api = new CoreV1Api(api)

  lazy val batchV1Api: BatchV1Api = new BatchV1Api(api)

  lazy val extensionsV1beta1Api: ExtensionsV1beta1Api = new ExtensionsV1beta1Api(api)

  def cache(implicit namespace: Namespace): K8sCache = caches.find(_.namespace.name == namespace.name).get

  def acquire()(implicit namespace: Namespace): Unit = {
    if (!caches.exists(_.namespace.name == namespace.name)) caches.add(new K8sCache(K8sCacheConfig(), namespace))
  }

  def release()(implicit namespace: Namespace): Unit = caches.find(_.namespace.name == namespace.name).foreach(_.close())

  def close(): Unit = {
    watch.close()
    caches.foreach(_.close())
  }
}
