package io.magnetic.vamp_core.operation.deployment

import _root_.io.magnetic.vamp_common.akka._
import _root_.io.magnetic.vamp_core.model.artifact._
import _root_.io.magnetic.vamp_core.operation.deployment.DeploymentSynchronizationActor.{Synchronize, SynchronizeAll}
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout
import io.magnetic.vamp_core.container_driver.{ContainerDriverActor, ContainerService}
import io.magnetic.vamp_core.model.artifact.DeploymentService._
import io.magnetic.vamp_core.operation.notification.OperationNotificationProvider

object DeploymentSynchronizationActor extends ActorDescription {

  def props(args: Any*): Props = Props[DeploymentSynchronizationActor]

  case class Synchronize(deployment: Deployment)

  case class SynchronizeAll(deployments: List[Deployment])

}

class DeploymentSynchronizationActor extends Actor with ActorLogging with ActorSupport with FutureSupport with ActorExecutionContextProvider with OperationNotificationProvider {

  def receive: Receive = {
    case Synchronize(deployment) => synchronize(deployment :: Nil)
    case SynchronizeAll(deployments) => synchronize(deployments)
  }

  private def synchronize(deployments: List[Deployment]) = {
    implicit val timeout: Timeout = ContainerDriverActor.timeout

    //val routes = offLoad(actorFor(RouterDriverActor) ? RouterDriverActor.All).asInstanceOf[List[ClusterRoute]]

    deployments.foreach(synchronizeContainer(_, offLoad(actorFor(ContainerDriverActor) ? ContainerDriverActor.All).asInstanceOf[List[ContainerService]]))
  }

  private def synchronizeContainer(deployment: Deployment, services: List[ContainerService]) = {
    deployment.clusters.foreach { cluster =>
      cluster.services.foreach { service =>
        val containerService = services.find(cs => cs.deploymentName == deployment.name && cs.breedName == service.breed.name)

        service.state match {
          case ReadyForDeployment(initiated, _) =>
            if (outOfSynchronization(service, containerService))
              actorFor(ContainerDriverActor) ! ContainerDriverActor.Deploy(deployment, service)

          case Deployed(initiated, _) =>
            if (outOfSynchronization(service, containerService))
              actorFor(ContainerDriverActor) ! ContainerDriverActor.Deploy(deployment, service)

          case ReadyForUndeployment(initiated, _) =>
            if (outOfSynchronization(service, containerService))
              actorFor(ContainerDriverActor) ! ContainerDriverActor.Undeploy(deployment, service)

          case Undeployed(initiated, _) =>
            if (outOfSynchronization(service, containerService))
              actorFor(ContainerDriverActor) ! ContainerDriverActor.Undeploy(deployment, service)

          case ReadyForRemoval(initiated, _) =>
            if (outOfSynchronization(service, containerService))
              actorFor(ContainerDriverActor) ! ContainerDriverActor.Undeploy(deployment, service)

          case _ =>
        }
      }
    }
  }

  private def outOfSynchronization(deploymentService: DeploymentService, containerService: Option[ContainerService]): Boolean = {
    false
  }
}

