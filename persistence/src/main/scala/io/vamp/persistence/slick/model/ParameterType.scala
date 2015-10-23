package io.vamp.persistence.slick.model

object ParameterType extends Enumeration {
  type ParameterType = Value
  val String, Int, Double = Value
}