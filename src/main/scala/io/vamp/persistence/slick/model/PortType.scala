package io.vamp.persistence.slick.model

object PortType extends Enumeration {
  type PortType = Value
  val HTTP, TCP = Value
}
