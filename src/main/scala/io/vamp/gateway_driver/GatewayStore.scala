package io.vamp.gateway_driver

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.gateway_driver.model.Gateway
import scala.concurrent.duration._

object GatewayStore {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.gateway-driver.response-timeout").seconds)

  sealed trait GatewayStoreMessage

  object Get extends GatewayStoreMessage

  case class Put(gateways: List[Gateway], raw: Option[Array[Byte]]) extends GatewayStoreMessage

}

trait GatewayStore