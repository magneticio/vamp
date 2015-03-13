package io.magnetic.vamp_core.operation.deployment

import _root_.io.magnetic.vamp_common.akka._
import _root_.io.magnetic.vamp_core.model.artifact._
import _root_.io.magnetic.vamp_core.operation.deployment.DeploymentSynchronizationActor.{Synchronize, SynchronizeAll}
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout
import io.magnetic.vamp_core._
import io.magnetic.vamp_core.container_driver.{ContainerDriverActor, ContainerServer, ContainerService}
import io.magnetic.vamp_core.model.artifact.DeploymentService._
import io.magnetic.vamp_core.operation.notification.OperationNotificationProvider
import io.magnetic.vamp_core.persistence.PersistenceActor
import io.magnetic.vamp_core.router_driver.{ClusterRoute, DeploymentRoutes, EndpointRoute, RouterDriverActor}

object DeploymentSynchronizationActor extends ActorDescription {

  def props(args: Any*): Props = Props[DeploymentSynchronizationActor]

  case class Synchronize(deployment: Deployment)

  case class SynchronizeAll(deployments: List[Deployment])

}

class DeploymentSynchronizationActor extends Actor with ActorLogging with ActorSupport with FutureSupport with ActorExecutionContextProvider with OperationNotificationProvider {

  private object Processed {

    trait State

    object Persist extends State

    case class UpdateRoute(ports: List[Port]) extends State

    object RemoveFromRoute extends State

    object RemoveFromPersistence extends State

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

    val deploymentRoutes = offLoad(actorFor(RouterDriverActor) ? RouterDriverActor.All).asInstanceOf[DeploymentRoutes]
    val containerServices = offLoad(actorFor(ContainerDriverActor) ? ContainerDriverActor.All).asInstanceOf[List[ContainerService]]

    deployments.foreach(synchronizeContainer(_, containerServices, deploymentRoutes))
  }

  private def synchronizeContainer(deployment: Deployment, containerServices: List[ContainerService], deploymentRoutes: DeploymentRoutes) = {
    val processedClusters = deployment.clusters.map { deploymentCluster =>
      val processedServices = deploymentCluster.services.map { deploymentService =>
        deploymentService.state match {
          case ReadyForDeployment(initiated, _) =>
            readyForDeployment(deployment, deploymentCluster, deploymentService, containerServices, deploymentRoutes.clusterRoutes)

          case Deployed(initiated, _) =>
            deployed(deployment, deploymentCluster, deploymentService, containerServices, deploymentRoutes.clusterRoutes)

          case ReadyForUndeployment(initiated, _) =>
            readyForUndeployment(deployment, deploymentCluster, deploymentService, containerServices, deploymentRoutes.clusterRoutes)

          case _ =>
            ProcessedService(Processed.Ignore, deploymentService)
        }
      }
      processServiceResults(deployment, deploymentCluster, deploymentRoutes.clusterRoutes, processedServices)
    }
    processClusterResults(deployment, processedClusters, deploymentRoutes.endpointRoutes)
  }

  private def readyForDeployment(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containerServices: List[ContainerService], clusterRoutes: List[ClusterRoute]): ProcessedService = {
    def convert(server: ContainerServer): DeploymentServer = {
      val ports = for {
        dp <- deploymentService.breed.ports.map(_.value.get)
        sp <- server.ports
      } yield (dp, sp)
      DeploymentServer(server.id, server.host, ports.toMap)
    }

    containerService(deployment, deploymentService, containerServices) match {
      case None =>
        if (deploymentService.breed.dependencies.forall({ case (n, d) =>
          deployment.clusters.flatMap(_.services).find(s => s.breed.name == d.name) match {
            case None => false
            case Some(service) => service.state.isInstanceOf[DeploymentService.Deployed]
          }
        })) {
          actorFor(ContainerDriverActor) ! ContainerDriverActor.Deploy(deployment, deploymentCluster, deploymentService)
        }

        ProcessedService(Processed.Ignore, deploymentService)

      case Some(cs) =>
        if (!matching(deploymentService, cs)) {
          if (matchingScale(deploymentService, cs)) {
            ProcessedService(Processed.Persist, deploymentService.copy(servers = cs.servers.map(convert)))
          } else {
            actorFor(ContainerDriverActor) ! ContainerDriverActor.Deploy(deployment, deploymentCluster, deploymentService)
            ProcessedService(Processed.Ignore, deploymentService)
          }
        } else {
          val ports = outOfSyncPorts(deployment, deploymentCluster, deploymentService, clusterRoutes)
          if (ports.isEmpty) {
            ProcessedService(Processed.Persist, deploymentService.copy(state = new DeploymentService.Deployed))
          } else {
            ProcessedService(Processed.UpdateRoute(ports), deploymentService)
          }
        }
    }
  }

  private def deployed(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containerServices: List[ContainerService], routes: List[ClusterRoute]): ProcessedService = {
    def redeploy() = {
      val ds = deploymentService.copy(state = new ReadyForDeployment())
      readyForDeployment(deployment, deploymentCluster, ds, containerServices, routes)
      ProcessedService(Processed.Persist, ds)
    }

    containerService(deployment, deploymentService, containerServices) match {
      case None => redeploy()
      case Some(cs) =>
        if (!matching(deploymentService, cs)) {
          redeploy()
        } else {
          val ports = outOfSyncPorts(deployment, deploymentCluster, deploymentService, routes)
          if (ports.isEmpty) {
            ProcessedService(Processed.Ignore, deploymentService)
          } else {
            redeploy()
          }
        }
    }
  }

  private def readyForUndeployment(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containerServices: List[ContainerService], routes: List[ClusterRoute]): ProcessedService = {
    if (deploymentService.breed.ports.forall({ port => clusterRouteService(deployment, deploymentCluster, deploymentService, port, routes).isEmpty})) {
      containerService(deployment, deploymentService, containerServices) match {
        case None =>
          ProcessedService(Processed.RemoveFromPersistence, deploymentService)
        case Some(cs) =>
          actorFor(ContainerDriverActor) ! ContainerDriverActor.Undeploy(deployment, deploymentService)
          ProcessedService(Processed.Ignore, deploymentService)
      }
    } else {
      ProcessedService(Processed.RemoveFromRoute, deploymentService)
    }
  }

  private def containerService(deployment: Deployment, deploymentService: DeploymentService, containerServices: List[ContainerService]): Option[ContainerService] =
    containerServices.find(_.matching(deployment, deploymentService.breed))

  private def matching(deploymentService: DeploymentService, containerService: ContainerService) =
    deploymentService.servers.size == containerService.servers.size && deploymentService.servers.forall(server => containerService.servers.exists(_.host == server.host))

  private def matchingScale(deploymentService: DeploymentService, containerService: ContainerService) =
  // TODO check on cpu and memory as well
    containerService.servers.size == deploymentService.scale.get.instances

  private def outOfSyncPorts(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, clusterRoutes: List[ClusterRoute]): List[Port] = {
    deploymentService.breed.ports.filter({ port =>
      clusterRouteService(deployment, deploymentCluster, deploymentService, port, clusterRoutes) match {
        case None => true
        case Some(routeService) => !matching(deploymentService, routeService)
      }
    })
  }

  private def matching(deploymentService: DeploymentService, routeService: router_driver.Service) =
    deploymentService.servers.size == routeService.servers.size && deploymentService.servers.forall(server => routeService.servers.exists(_.host == server.host))

  private def clusterRouteService(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, port: Port, clusterRoutes: List[ClusterRoute]): Option[router_driver.Service] =
    clusterRoutes.find(_.matching(deployment, deploymentCluster, port)) match {
      case None => None
      case Some(route) => route.services.find(_.name == deploymentService.breed.name)
    }

  private def processServiceResults(deployment: Deployment, deploymentCluster: DeploymentCluster, clusterRoutes: List[ClusterRoute], processedServices: List[ProcessedService]): ProcessedCluster = {

    val processedCluster = if (processedServices.exists(s => s.state == Processed.Persist || s.state == Processed.RemoveFromPersistence)) {

      val services = processedServices.filter(_.state != Processed.RemoveFromPersistence).map(_.service)

      val parameters = processedServices.filter(ps => ps.state == Processed.Persist && ps.service.state.isInstanceOf[Deployed]).map(_.service.breed).flatMap(_.ports).map({ port =>
        Trait.Name(Some(deploymentCluster.name), Some(Trait.Name.Group.Ports), port.name.value) -> deploymentCluster.routes.get(port.value.get).get
      }).toMap ++ deploymentCluster.parameters

      val dc = deploymentCluster.copy(services = services, parameters = parameters)

      ProcessedCluster(if (dc.services.isEmpty) Processed.RemoveFromPersistence else Processed.Persist, dc)
    } else {
      ProcessedCluster(Processed.Ignore, deploymentCluster)
    }

    val ports: Set[Port] = processedServices.flatMap(processed => processed.state match {
      case Processed.RemoveFromRoute => processed.service.breed.ports
      case Processed.UpdateRoute(p) => p
      case _ => Nil
    }).toSet

    if (ports.nonEmpty) {
      val cluster = processedCluster.cluster.copy(services = processedServices.filter(_.state != Processed.RemoveFromRoute).map(_.service))
      if (cluster.services.nonEmpty)
        ports.foreach(port => actorFor(RouterDriverActor) ! RouterDriverActor.Update(deployment, cluster, port))
      else
        ports.foreach(port => actorFor(RouterDriverActor) ! RouterDriverActor.Remove(deployment, cluster, port))
    }

    processedCluster
  }

  private def processClusterResults(deployment: Deployment, processedCluster: List[ProcessedCluster], routes: List[EndpointRoute]) = {
    if (processedCluster.exists(pc => pc.state == Processed.Persist || pc.state == Processed.RemoveFromPersistence)) {
      val d = deployment.copy(clusters = processedCluster.filter(_.state != Processed.RemoveFromPersistence).map(_.cluster))
      updateEndpoints(d, routes)
      if (d.clusters.isEmpty)
        actorFor(PersistenceActor) ! PersistenceActor.Delete(d.name, classOf[Deployment])
      else
        actorFor(PersistenceActor) ! PersistenceActor.Update(d)
    }
  }

  private def updateEndpoints(deployment: Deployment, routes: List[EndpointRoute]) = {
    deployment.endpoints.foreach(port => port match {
      case TcpPort(Trait.Name(Some(scope), Some(group), value), None, Some(number), Trait.Direction.Out) => process(port, number)
      case HttpPort(Trait.Name(Some(scope), Some(group), value), None, Some(number), Trait.Direction.Out) => process(port, number)
      case _ =>
    })

    def process(port: Port, number: Int) = {
      (deployment.clusters.find(_.name == port.name.scope.get), routes.find(_.matching(deployment, port))) match {
        case (None, Some(route)) => actorFor(RouterDriverActor) ! RouterDriverActor.RemoveEndpoint(deployment, port)
        case (Some(cluster), None) => actorFor(RouterDriverActor) ! RouterDriverActor.UpdateEndpoint(deployment, port)
        case _ =>
      }
    }
  }
}
