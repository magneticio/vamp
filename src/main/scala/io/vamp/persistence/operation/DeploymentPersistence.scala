package io.vamp.persistence.operation

import io.vamp.model.artifact._

object DeploymentPersistence {

  def serviceArtifactName(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    GatewayPath(deployment.name :: cluster.name :: service.breed.name :: Nil).normalized
  }

  def servicePortArtifactName(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, port: Port) = {
    GatewayPath(deployment.name :: cluster.name :: service.breed.name :: port.name :: Nil).normalized
  }
}

case class DeploymentServiceState(name: String, state: DeploymentService.State) extends Artifact {
  val kind = "deployment-service-state"
}

case class DeploymentServiceInstances(name: String, instances: List[DeploymentInstance]) extends Artifact {
  val kind = "deployment-service-instances"
}

case class DeploymentServiceEnvironmentVariables(name: String, environmentVariables: List[EnvironmentVariable]) extends Artifact {
  val kind = "deployment-service-environment-variables"
}
