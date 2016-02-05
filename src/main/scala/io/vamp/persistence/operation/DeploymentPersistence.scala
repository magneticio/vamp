package io.vamp.persistence.operation

import io.vamp.model.artifact._

object DeploymentPersistence {

  def serviceArtifactName(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    GatewayPath(deployment.name :: cluster.name :: service.breed.name :: Nil).normalized
  }
}

case class DeploymentServiceState(name: String, state: DeploymentService.State) extends Artifact

case class DeploymentServiceInstances(name: String, instances: List[DeploymentInstance]) extends Artifact

case class DeploymentServiceEnvironmentVariables(name: String, environmentVariables: List[EnvironmentVariable]) extends Artifact
