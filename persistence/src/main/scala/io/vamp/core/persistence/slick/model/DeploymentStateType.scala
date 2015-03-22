package io.vamp.core.persistence.slick.model

object DeploymentStateType extends Enumeration {
  type DeploymentStateType = Value
  val ReadyForDeployment, Deployed, ReadyForUndeployment, Error = Value
}
