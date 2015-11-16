package io.vamp.gateway_driver

import io.vamp.gateway_driver.model.Gateway

trait GatewayMarshaller {

  def info: AnyRef

  def marshall(gateways: List[Gateway]): Array[Byte]
}
