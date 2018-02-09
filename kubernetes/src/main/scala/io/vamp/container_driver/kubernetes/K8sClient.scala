package io.vamp.container_driver.kubernetes

import java.io.FileInputStream

import io.kubernetes.client.ApiClient
import io.kubernetes.client.apis.{ ApisApi, BatchV1Api, CoreV1Api, ExtensionsV1beta1Api }

import scala.collection.mutable
import scala.io.Source
import scala.util.Try

private case class SharedK8sClient(client: K8sClient, counter: Int)

object K8sClient {

  private val clients = new mutable.HashMap[K8sConfig, SharedK8sClient]()

  def acquire(config: K8sConfig): K8sClient = synchronized {
    clients.get(config) match {
      case Some(shared) ⇒
        clients.put(config, shared.copy(counter = shared.counter + 1))
        shared.client
      case None ⇒
        val shared = SharedK8sClient(new K8sClient(config), 1)
        clients.put(config, shared)
        shared.client
    }
  }

  def release(config: K8sConfig): Unit = synchronized {
    clients.get(config) match {
      case Some(shared) if shared.counter == 1 ⇒
        clients.remove(config)
        shared.client.close()
      case Some(shared) ⇒ clients.put(config, shared.copy(counter = shared.counter - 1))
      case None         ⇒
    }
  }
}

class K8sClient(val config: K8sConfig) {

  private val api: ApiClient = {
    val client = new ApiClient()
    client.setBasePath(config.url)
    val apiKey = if (config.bearer.nonEmpty) config.bearer else Try(Source.fromFile(config.token).mkString).getOrElse("")
    if (apiKey.nonEmpty) client.setApiKey(s"Bearer $apiKey")
    if (config.username.nonEmpty) client.setUsername(config.username)
    if (config.password.nonEmpty) client.setPassword(config.password)
    if (config.serverCaCert.nonEmpty) client.setSslCaCert(new FileInputStream(config.serverCaCert))
    client.setVerifyingSsl(config.tlsCheck)
  }

  lazy val cache = new K8sCache(config.cache)

  lazy val apisApi: ApisApi = new ApisApi(api)

  lazy val coreV1Api: CoreV1Api = new CoreV1Api(api)

  lazy val batchV1Api: BatchV1Api = new BatchV1Api(api)

  lazy val extensionsV1beta1Api: ExtensionsV1beta1Api = new ExtensionsV1beta1Api(api)

  def close(): Unit = cache.close()
}
