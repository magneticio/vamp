package io.vamp.persistence.db

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.model.artifact._

import scala.concurrent.Future

object DevelopmentPersistenceMessages extends DevelopmentPersistenceMessages

trait DevelopmentPersistenceMessages {

  case class UpdateDeploymentServiceState(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, state: DeploymentService.State) extends PersistenceActor.PersistenceMessages

  case class UpdateDeploymentServiceScale(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, scale: DefaultScale) extends PersistenceActor.PersistenceMessages

  case class UpdateDeploymentServiceInstances(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, instances: List[DeploymentInstance]) extends PersistenceActor.PersistenceMessages

  case class UpdateDeploymentServiceEnvironmentVariables(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, environmentVariables: List[EnvironmentVariable]) extends PersistenceActor.PersistenceMessages

  case class ResetDeploymentService(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) extends PersistenceActor.PersistenceMessages

}

private[persistence] object DevelopmentPersistenceOperations {

  def serviceArtifactName(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    GatewayPath(deployment.name :: cluster.name :: service.breed.name :: Nil).normalized
  }

  def servicePortArtifactName(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, port: Port) = {
    GatewayPath(deployment.name :: cluster.name :: service.breed.name :: port.name :: Nil).normalized
  }
}

trait DevelopmentPersistenceOperations {
  this: CommonSupportForActors ⇒

  import DevelopmentPersistenceMessages._
  import DevelopmentPersistenceOperations._

  implicit def timeout: Timeout

  protected def receiveDevelopment: Actor.Receive = {

    case o: UpdateDeploymentServiceState                ⇒ updateDeploymentServiceState(o.deployment, o.cluster, o.service, o.state)

    case o: UpdateDeploymentServiceScale                ⇒ updateUpdateDeploymentServiceScale(o.deployment, o.cluster, o.service, o.scale)

    case o: UpdateDeploymentServiceInstances            ⇒ updateUpdateDeploymentServiceInstances(o.deployment, o.cluster, o.service, o.instances)

    case o: UpdateDeploymentServiceEnvironmentVariables ⇒ updateDeploymentServiceEnvironmentVariables(o.deployment, o.cluster, o.service, o.environmentVariables)

    case o: ResetDeploymentService                      ⇒ resetDeploymentService(o.deployment, o.cluster, o.service)
  }

  private def updateDeploymentServiceState(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, state: DeploymentService.State) = reply {
    self ? PersistenceActor.Update(DeploymentServiceState(serviceArtifactName(deployment, cluster, service), state))
  }

  private def updateUpdateDeploymentServiceScale(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, scale: DefaultScale) = reply {
    self ? PersistenceActor.Update(DeploymentServiceScale(serviceArtifactName(deployment, cluster, service), scale))
  }

  private def updateUpdateDeploymentServiceInstances(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, instances: List[DeploymentInstance]) = reply {
    self ? PersistenceActor.Update(DeploymentServiceInstances(serviceArtifactName(deployment, cluster, service), instances))
  }

  private def updateDeploymentServiceEnvironmentVariables(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, environmentVariables: List[EnvironmentVariable]) = reply {
    self ? PersistenceActor.Update(DeploymentServiceEnvironmentVariables(serviceArtifactName(deployment, cluster, service), environmentVariables))
  }

  private def resetDeploymentService(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = reply {
    val name = serviceArtifactName(deployment, cluster, service)

    val messages = PersistenceActor.Delete(name, classOf[DeploymentServiceScale]) ::
      PersistenceActor.Delete(name, classOf[DeploymentServiceInstances]) ::
      PersistenceActor.Delete(name, classOf[DeploymentServiceEnvironmentVariables]) :: Nil

    Future.sequence(messages.map(self ? _))
  }
}

private[persistence] case class DeploymentServiceState(name: String, state: DeploymentService.State) extends Artifact {
  val kind = "deployment-service-state"
}

private[persistence] case class DeploymentServiceScale(name: String, scale: DefaultScale) extends Artifact {
  val kind = "deployment-service-scale"
}

private[persistence] case class DeploymentServiceInstances(name: String, instances: List[DeploymentInstance]) extends Artifact {
  val kind = "deployment-service-instances"
}

private[persistence] case class DeploymentServiceEnvironmentVariables(name: String, environmentVariables: List[EnvironmentVariable]) extends Artifact {
  val kind = "deployment-service-environment-variables"
}
