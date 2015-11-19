package io.vamp.gateway_driver.logstash

import com.typesafe.config.ConfigFactory

object Logstash {

  val `type` = "haproxy"

  val index = ConfigFactory.load().getString("vamp.gateway-driver.logstash.index")
}
