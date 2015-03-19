package io.magnetic.vamp_core.persistence.slick.model

object DeploymentStateType extends Enumeration {
  type DeploymentStateType = Value
  val ReadyForDeployment, Deployed, ReadyForUndeployment, Error = Value
}
