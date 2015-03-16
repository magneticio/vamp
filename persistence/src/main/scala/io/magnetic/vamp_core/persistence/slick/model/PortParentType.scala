package io.magnetic.vamp_core.persistence.slick.model

/**
 * Port parent
 */
object PortParentType extends Enumeration {
  type PortParentType = Value
  val Breed, BlueprintEndpoint, BlueprintParameter = Value
}
