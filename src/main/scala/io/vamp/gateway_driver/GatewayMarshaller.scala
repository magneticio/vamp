package io.vamp.gateway_driver

import io.vamp.model.artifact._

abstract class GatewayMarshaller(val keyValue: Boolean, file: String, resource: String, parameters: Map[String, AnyRef]) {

  def info: AnyRef

  def `type`: String

  def marshall(gateways: List[Gateway], template: Option[String]): String

  protected def parameter[T](name: String): T = parameters(name).asInstanceOf[T]
}
