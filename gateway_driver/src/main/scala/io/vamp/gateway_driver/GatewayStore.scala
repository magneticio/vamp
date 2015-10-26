package io.vamp.gateway_driver

import io.vamp.gateway_driver.model.Gateway

import scala.concurrent.Future

trait GatewayStore {

  def info: Future[Any]

  def read(): Future[List[Gateway]]

  def write(gateways: List[Gateway], raw: Option[Array[Byte]]): Unit
}
