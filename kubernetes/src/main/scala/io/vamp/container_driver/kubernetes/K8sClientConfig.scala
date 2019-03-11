package io.vamp.container_driver.kubernetes

import io.vamp.common.{ Config, Namespace }

import scala.concurrent.duration.FiniteDuration

object K8sClientConfig {

  import KubernetesContainerDriver._

  def apply(kubernetesNamespace: String)(implicit namespace: Namespace): K8sClientConfig = {
    K8sClientConfig(
      url = Config.string(s"$config.url")(),
      bearer = Config.string(s"$config.bearer")(),
      token = Config.string(s"$config.token")(),
      username = Config.string(s"$config.username")(),
      password = Config.string(s"$config.password")(),
      serverCaCert = Config.string(s"$config.server-ca-cert")(),
      clientCert = Config.string(s"$config.client-cert")(),
      privateKey = Config.string(s"$config.private-key")(),
      namespace = kubernetesNamespace,
      tlsCheck = Config.boolean(s"$config.tls-check")()
    )
  }
}

case class K8sClientConfig(
  url:          String,
  bearer:       String,
  token:        String,
  username:     String,
  password:     String,
  serverCaCert: String,
  clientCert:   String,
  privateKey:   String,
  namespace:    String,
  tlsCheck:     Boolean
)

object K8sCacheConfig {

  import KubernetesContainerDriver._

  def apply()(implicit namespace: Namespace): K8sCacheConfig = {
    K8sCacheConfig(
      readTimeToLivePeriod = Config.duration(s"$config.cache.read-time-to-live")(),
      writeTimeToLivePeriod = Config.duration(s"$config.cache.write-time-to-live")(),
      failureTimeToLivePeriod = Config.duration(s"$config.cache.failure-time-to-live")()
    )
  }
}

case class K8sCacheConfig(
  readTimeToLivePeriod:    FiniteDuration,
  writeTimeToLivePeriod:   FiniteDuration,
  failureTimeToLivePeriod: FiniteDuration
)
