package io.vamp.core.persistence.slick.model

/**
 * Port parent
 */
object PortParentType extends Enumeration {
  type PortParentType = Value
  val Breed, BlueprintEndpoint, DeploymentEndPoint, DeploymentPort = Value
}
