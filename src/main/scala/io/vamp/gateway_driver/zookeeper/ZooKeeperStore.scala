package io.vamp.gateway_driver.zookeeper

import io.vamp.gateway_driver.GatewayStore
import io.vamp.gateway_driver.model.Gateway

import scala.concurrent.Future

trait ZooKeeperStore extends GatewayStore {

  override def info: Future[Any] = Future.successful(None)

  override def read(): Future[List[Gateway]] = Future.successful(Nil)

  override def write(gateways: List[Gateway], raw: Option[Array[Byte]]) = {}
}
