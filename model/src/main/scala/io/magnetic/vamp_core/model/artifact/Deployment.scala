package io.magnetic.vamp_core.model.artifact

object Deployment {

  object State extends Enumeration {
    val PreparedForDeployment, Deployed, PreparedForUndeployment, Undeployed, PreparedForRemoval = Value
  }

}

trait DeploymentState {
  def state: Deployment.State.Value
}

case class Deployment(name: String, state: Deployment.State.Value, clusters: List[DeploymentCluster], endpoints: Map[Trait.Name, String], parameters: Map[Trait.Name, String]) extends Blueprint with DeploymentState

case class DeploymentCluster(name: String, state: Deployment.State.Value, services: List[DeploymentService], sla: Option[Sla]) extends AbstractCluster with DeploymentState

case class DeploymentService(state: Deployment.State.Value, breed: DefaultBreed, scale: Option[Scale], routing: Option[Routing]) extends AbstractService with DeploymentState
