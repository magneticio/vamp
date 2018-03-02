package io.vamp.container_driver.marathon

import io.vamp.common.http.HttpClient
import io.vamp.common.{ Config, Namespace }

import scala.concurrent.duration.FiniteDuration

object MarathonClientConfig {

  import MarathonDriverActor._

  def apply()(implicit namespace: Namespace): MarathonClientConfig = {
    MarathonClientConfig(
      mesosUrl = Config.string(s"$mesosConfig.url")(),
      marathonUrl = Config.string(s"$marathonConfig.url")(),
      headers = {
        val token = Config.string(s"$marathonConfig.token")()
        if (token.isEmpty)
          HttpClient.basicAuthorization(Config.string(s"$marathonConfig.user")(), Config.string(s"$marathonConfig.password")())
        else
          List("Authorization" → s"token=$token")
      },

      tlsCheck = HttpClient.tlsCheck()
    )
  }
}

case class MarathonClientConfig(
  mesosUrl:    String,
  marathonUrl: String,
  headers:     List[(String, String)],
  tlsCheck:    Boolean
)

object MarathonCacheConfig {

  import MarathonDriverActor._

  def apply()(implicit namespace: Namespace): MarathonCacheConfig = {
    MarathonCacheConfig(
      sse = Config.boolean(s"$marathonConfig.sse")(),
      readTimeToLivePeriod = Config.duration(s"$marathonConfig.cache.read-time-to-live")(),
      writeTimeToLivePeriod = Config.duration(s"$marathonConfig.cache.write-time-to-live")(),
      failureTimeToLivePeriod = Config.duration(s"$marathonConfig.cache.failure-time-to-live")()
    )
  }
}

case class MarathonCacheConfig(
  sse:                     Boolean,
  readTimeToLivePeriod:    FiniteDuration,
  writeTimeToLivePeriod:   FiniteDuration,
  failureTimeToLivePeriod: FiniteDuration
)
