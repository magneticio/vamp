package io.vamp.container_driver.kubernetes

import java.io.FileInputStream
import java.lang.reflect.Type
import java.util

import akka.event.LoggingAdapter
import com.squareup.okhttp.{ Call, OkHttpClient, Request }
import io.kubernetes.client.apis.{ ApisApi, BatchV1Api, CoreV1Api, ExtensionsV1beta1Api }
import io.kubernetes.client.{ ApiCallback, ApiClient, ApiResponse, Pair, ProgressRequestBody }
import io.vamp.common.{ CacheStore, Config, Namespace }

import scala.concurrent.duration.FiniteDuration
import scala.io.Source
import scala.util.Try

object K8sConfig {

  import KubernetesContainerDriver._

  def apply()(implicit namespace: Namespace): K8sConfig = {
    K8sConfig(
      url = Config.string(s"$config.url")(),
      bearer = Config.string(s"$config.bearer")(),
      token = Config.string(s"$config.token")(),
      username = Config.string(s"$config.username")(),
      password = Config.string(s"$config.password")(),
      serverCaCert = Config.string(s"$config.server-ca-cert")(),
      tlsCheck = Config.boolean(s"$config.tls-check")(),
      cache = K8sCacheConfig(
        readTimeToLivePeriod = Config.duration(s"$config.cache.read-time-to-live")(),
        writeTimeToLivePeriod = Config.duration(s"$config.cache.write-time-to-live")(),
        failureTimeToLivePeriod = Config.duration(s"$config.cache.failure-time-to-live")()
      )
    )
  }
}

case class K8sConfig(
  url:          String,
  bearer:       String,
  token:        String,
  username:     String,
  password:     String,
  serverCaCert: String,
  tlsCheck:     Boolean,
  cache:        K8sCacheConfig
)

case class K8sCacheConfig(
  readTimeToLivePeriod:    FiniteDuration,
  writeTimeToLivePeriod:   FiniteDuration,
  failureTimeToLivePeriod: FiniteDuration
)

class K8sClient(val config: K8sConfig, log: LoggingAdapter) {

  private lazy val cache = new CacheStore()

  private val api: ApiClient = {
    val client = new ApiClient() {

      override def buildCall(path: String, method: String, queryParams: util.List[Pair], collectionQueryParams: util.List[Pair], body: scala.Any, headerParams: util.Map[String, String], formParams: util.Map[String, AnyRef], authNames: Array[String], progressRequestListener: ProgressRequestBody.ProgressRequestListener): Call = {
        val request = buildRequest(path, method, queryParams, collectionQueryParams, body, headerParams, formParams, authNames, progressRequestListener)
        new ExtCall(api.getHttpClient, request)
      }

      override def execute[T](call: Call, returnType: Type): ApiResponse[T] = call match {
        case c: ExtCall ⇒ executeWithCache(c, returnType)
        case _          ⇒ throw new NotImplementedError
      }

      override def executeAsync[T](call: Call, returnType: Type, callback: ApiCallback[T]): Unit = throw new NotImplementedError

      private def executeWithCache[T](call: ExtCall, returnType: Type): ApiResponse[T] = {
        val id = s"${call.request.method()} ${call.request.url()}"
        cache.get[ApiResponse[T]](id) match {
          case Some(response) ⇒
            log.info(s"cache get: $id")
            response
          case None ⇒
            val response = super.execute[T](call, returnType)
            val ttl = timeToLivePeriod(call.request.method(), response)
            log.info(s"cache put [${ttl.toSeconds} s]: $id")
            cache.put(id, response, ttl)
            response
        }
      }

      private def timeToLivePeriod[T](method: String, response: ApiResponse[T]): FiniteDuration = {
        if (response.getStatusCode >= 200 && response.getStatusCode < 300) {
          if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) config.cache.readTimeToLivePeriod
          else config.cache.writeTimeToLivePeriod
        }
        else config.cache.failureTimeToLivePeriod
      }
    }

    client.setBasePath(config.url)
    val apiKey = if (config.bearer.nonEmpty) config.bearer else Try(Source.fromFile(config.token).mkString).getOrElse("")
    if (apiKey.nonEmpty) client.setApiKey(s"Bearer $apiKey")
    if (config.username.nonEmpty) client.setUsername(config.username)
    if (config.password.nonEmpty) client.setPassword(config.password)
    if (config.serverCaCert.nonEmpty) client.setSslCaCert(new FileInputStream(config.serverCaCert))
    client.setVerifyingSsl(config.tlsCheck)
  }

  lazy val apisApi: ApisApi = new ApisApi(api)

  lazy val coreV1Api: CoreV1Api = new CoreV1Api(api)

  lazy val batchV1Api: BatchV1Api = new BatchV1Api(api)

  lazy val extensionsV1beta1Api: ExtensionsV1beta1Api = new ExtensionsV1beta1Api(api)

  def close(): Unit = cache.close()
}

private class ExtCall(client: OkHttpClient, val request: Request) extends Call(client, request)
