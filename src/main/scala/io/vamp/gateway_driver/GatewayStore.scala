package io.vamp.gateway_driver

import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object GatewayStore {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.gateway-driver.response-timeout").seconds)

  sealed trait GatewayStoreMessage

  object Get extends GatewayStoreMessage

  case class Put(data: Option[String]) extends GatewayStoreMessage

}

trait GatewayStore