package io.vamp.container_driver.kubernetes

import java.io.FileInputStream

import io.kubernetes.client.ApiClient
import io.kubernetes.client.util.{ Config ⇒ K8sClientConfig }
import io.vamp.common.{ Config, Namespace }

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
      tlsCheck = Config.boolean(s"$config.tls-check")()
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
  tlsCheck:     Boolean
)

class K8sClient(config: K8sConfig) {
  val api: ApiClient = {
    val client = K8sClientConfig.defaultClient.setBasePath(config.url)
    val apiKey = if (config.bearer.nonEmpty) config.bearer else Try(Source.fromFile(config.token).mkString).getOrElse("")
    if (apiKey.nonEmpty) client.setApiKey(apiKey)
    if (config.username.nonEmpty) client.setUsername(config.username)
    if (config.password.nonEmpty) client.setPassword(config.password)
    if (config.serverCaCert.nonEmpty) client.setSslCaCert(new FileInputStream(config.serverCaCert))
    client.setVerifyingSsl(config.tlsCheck)
  }
}
