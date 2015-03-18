package io.magnetic.vamp_core.persistence.slick.model

/**
 * Created by Matthijs Dekker on 18/03/15.
 */
object DeploymentStateType extends Enumeration {
  type DeploymentStateType = Value
  val ReadyForDeployment, Deployed, ReadyForUndeployment, Error = Value
}
