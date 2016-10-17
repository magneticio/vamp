package io.vamp.persistence.db

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.model.artifact._

import scala.concurrent.Future

object DevelopmentPersistenceMessages extends DevelopmentPersistenceMessages

trait DevelopmentPersistenceMessages {

  case class UpdateDeploymentServiceStatus(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, status: DeploymentService.Status) extends PersistenceActor.PersistenceMessages

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

    case o: UpdateDeploymentServiceStatus               ⇒ updateStatus(o.deployment, o.cluster, o.service, o.status)

    case o: UpdateDeploymentServiceScale                ⇒ updateServiceScale(o.deployment, o.cluster, o.service, o.scale)

    case o: UpdateDeploymentServiceInstances            ⇒ updateServiceInstances(o.deployment, o.cluster, o.service, o.instances)

    case o: UpdateDeploymentServiceEnvironmentVariables ⇒ updateEnvironmentVariables(o.deployment, o.cluster, o.service, o.environmentVariables)

    case o: ResetDeploymentService                      ⇒ reset(o.deployment, o.cluster, o.service)
  }

  private def updateStatus(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, status: DeploymentService.Status) = reply {
    self ? PersistenceActor.Update(DeploymentServiceStatus(serviceArtifactName(deployment, cluster, service), status))
  }

  private def updateServiceScale(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, scale: DefaultScale) = reply {
    self ? PersistenceActor.Update(DeploymentServiceScale(serviceArtifactName(deployment, cluster, service), scale))
  }

  private def updateServiceInstances(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, instances: List[DeploymentInstance]) = reply {
    self ? PersistenceActor.Update(DeploymentServiceInstances(serviceArtifactName(deployment, cluster, service), instances))
  }

  private def updateEnvironmentVariables(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, environmentVariables: List[EnvironmentVariable]) = reply {
    self ? PersistenceActor.Update(DeploymentServiceEnvironmentVariables(serviceArtifactName(deployment, cluster, service), environmentVariables))
  }

  private def reset(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = reply {
    val name = serviceArtifactName(deployment, cluster, service)

    val messages = PersistenceActor.Delete(name, classOf[DeploymentServiceScale]) ::
      PersistenceActor.Delete(name, classOf[DeploymentServiceInstances]) ::
      PersistenceActor.Delete(name, classOf[DeploymentServiceEnvironmentVariables]) :: Nil

    Future.sequence(messages.map(self ? _))
  }
}

private[persistence] case class DeploymentServiceStatus(name: String, status: DeploymentService.Status) extends Artifact {
  val kind = "deployment-service-statuses"
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
