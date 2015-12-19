package io.vamp.gateway_driver

import io.vamp.model.artifact.Gateway

trait GatewayMarshaller {

  def info: AnyRef

  def path: List[String]

  def marshall(gateways: List[Gateway]): String
}
