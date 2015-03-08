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
import io.magnetic.vamp_core.persistence.PersistenceActor
import io.magnetic.vamp_core.router_driver.{ClusterRoute, RouterDriverActor}

object DeploymentSynchronizationActor extends ActorDescription {

  def props(args: Any*): Props = Props[DeploymentSynchronizationActor]

  case class Synchronize(deployment: Deployment)

  case class SynchronizeAll(deployments: List[Deployment])

}

class DeploymentSynchronizationActor extends Actor with ActorLogging with ActorSupport with FutureSupport with ActorExecutionContextProvider with OperationNotificationProvider {

  private object Processed {

    trait State

    object Persist extends State

    case class UpdateRoute(port: Port) extends State

    object Remove extends State

    object Ignore extends State

  }

  private case class ProcessedService(state: Processed.State, service: DeploymentService)

  private case class ProcessedCluster(state: Processed.State, cluster: DeploymentCluster)

  def receive: Receive = {
    case Synchronize(deployment) => synchronize(deployment :: Nil)
    case SynchronizeAll(deployments) => synchronize(deployments)
  }

  private def synchronize(deployments: List[Deployment]) = {
    implicit val timeout: Timeout = ContainerDriverActor.timeout

    val clusterRoutes = offLoad(actorFor(RouterDriverActor) ? RouterDriverActor.All).asInstanceOf[List[ClusterRoute]]
    val containerServices = offLoad(actorFor(ContainerDriverActor) ? ContainerDriverActor.All).asInstanceOf[List[ContainerService]]

    deployments.foreach(synchronizeContainer(_, containerServices, clusterRoutes))
  }

  private def synchronizeContainer(deployment: Deployment, containerServices: List[ContainerService], clusterRoutes: List[ClusterRoute]) = {
    val processedClusters = deployment.clusters.map { deploymentCluster =>
      val processedServices = deploymentCluster.services.map { deploymentService =>
        deploymentService.state match {
          case ReadyForDeployment(initiated, _) =>
            readyForDeployment(deploymentService, containerServices, clusterRoutes)

          case Deployed(initiated, _) =>
            deployed(deploymentService, containerServices, clusterRoutes)

          case ReadyForUndeployment(initiated, _) =>
            readyForUndeployment(deploymentService, containerServices, clusterRoutes)

          case Undeployed(initiated, _) =>
            undeployed(deploymentService, containerServices, clusterRoutes)

          case ReadyForRemoval(initiated, _) =>
            readyForRemoval(deploymentService, containerServices, clusterRoutes)

          case Removed(initiated, _) =>
            ProcessedService(Processed.Remove, deploymentService)

          case _ =>
            ProcessedService(Processed.Ignore, deploymentService)
        }
      }
      processServiceResults(deployment, deploymentCluster, processedServices)
    }
    processClusterResults(deployment, processedClusters)
  }

  private def readyForDeployment(deploymentService: DeploymentService, services: List[ContainerService], routes: List[ClusterRoute]): ProcessedService = {
    //TODO actorFor(ContainerDriverActor) ! ContainerDriverActor.Deploy(deployment, service)

    ProcessedService(Processed.Ignore, deploymentService)
  }

  private def deployed(deploymentService: DeploymentService, services: List[ContainerService], routes: List[ClusterRoute]): ProcessedService = {
    //TODO
    ProcessedService(Processed.Ignore, deploymentService)
  }

  private def readyForUndeployment(deploymentService: DeploymentService, services: List[ContainerService], routes: List[ClusterRoute]): ProcessedService = {
    //TODO
    ProcessedService(Processed.Ignore, deploymentService)
  }

  private def undeployed(deploymentService: DeploymentService, services: List[ContainerService], routes: List[ClusterRoute]): ProcessedService = {
    //TODO
    ProcessedService(Processed.Ignore, deploymentService)
  }

  private def readyForRemoval(deploymentService: DeploymentService, services: List[ContainerService], routes: List[ClusterRoute]): ProcessedService = {
    //TODO
    ProcessedService(Processed.Ignore, deploymentService)
  }

  private def containerService(deployment: Deployment, deploymentService: DeploymentService, containerServices: List[ContainerService]): Option[ContainerService] =
    containerServices.find(cs => cs.deploymentName == deployment.name && cs.breedName == deploymentService.breed.name)

  private def clusterRoute(deployment: Deployment, deploymentCluster: DeploymentCluster, portNumber: Int, clusterRoutes: List[ClusterRoute]): Option[ClusterRoute] =
    clusterRoutes.find(r => r.deploymentName == deployment.name && r.clusterName == deploymentCluster.name && portNumber == r.portNumber)

  private def processServiceResults(deployment: Deployment, deploymentCluster: DeploymentCluster, processedServices: List[ProcessedService]): ProcessedCluster = {
    val updated = processedServices.map({
      _.state match {
        case Processed.UpdateRoute(port) =>
          actorFor(RouterDriverActor) ! RouterDriverActor.Update(deployment, deploymentCluster, port)
          false

        case Processed.Persist => true
        case Processed.Remove => true
        case _ => false
      }
    }).foldLeft(false)((s1, s2) => s1 || s2)

    if (updated) {
      val dc = deploymentCluster.copy(services = processedServices.filter(_.state != Processed.Remove).map(_.service))
      ProcessedCluster(if (dc.services.isEmpty) Processed.Remove else Processed.Persist, dc)
    } else
      ProcessedCluster(Processed.Ignore, deploymentCluster)
  }

  private def processClusterResults(deployment: Deployment, processedCluster: List[ProcessedCluster]) = {
    if (processedCluster.exists(pc => pc.state == Processed.Persist || pc.state == Processed.Remove)) {
      val d = deployment.copy(clusters = processedCluster.filter(_.state != Processed.Remove).map(_.cluster))
      actorFor(PersistenceActor) ! PersistenceActor.Update(d)
    }
  }
}
