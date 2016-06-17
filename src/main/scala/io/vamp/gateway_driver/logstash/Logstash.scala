package io.vamp.gateway_driver.logstash

import io.vamp.common.config.Config

object Logstash {

  val `type` = "haproxy"

  val index = Config.string("vamp.gateway-driver.logstash.index")
}
