package io.vamp.gateway_driver

import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

object GatewayStore {

  val path = List[String]("gateway")

  val timeout = Timeout(ConfigFactory.load().getInt("vamp.gateway-driver.response-timeout").seconds)

  sealed trait GatewayStoreMessage

  case class Create(path: List[String]) extends GatewayStoreMessage

  case class Get(path: List[String]) extends GatewayStoreMessage

  case class Put(path: List[String], data: Option[String]) extends GatewayStoreMessage

}

trait GatewayStore {

  protected def pathToString(path: List[String]) = path.mkString("/")
}