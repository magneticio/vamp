package io.vamp.core.persistence.slick.model

/**
 * Parameter parent
 */
object ParameterParentType extends Enumeration {
  type ParameterParentType = Value
  val Escalation, Sla = Value
}