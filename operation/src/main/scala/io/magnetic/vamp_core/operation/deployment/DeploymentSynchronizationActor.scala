package io.magnetic.vamp_core.operation.deployment

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import _root_.io.vamp.common.akka._
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_core._
import io.magnetic.vamp_core.container_driver.{ContainerDriverActor, ContainerServer, ContainerService}
import io.magnetic.vamp_core.model.artifact.DeploymentService._
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.operation.deployment.DeploymentSynchronizationActor.{Synchronize, SynchronizeAll}
import io.magnetic.vamp_core.operation.notification.{DeploymentServiceError, InternalServerError, OperationNotificationProvider}
import io.magnetic.vamp_core.router_driver.{ClusterRoute, DeploymentRoutes, EndpointRoute, RouterDriverActor}
import io.vamp.core.persistence.actor.PersistenceActor

object DeploymentSynchronizationActor extends ActorDescription {

  def props(args: Any*): Props = Props[DeploymentSynchronizationActor]

  object SynchronizeAll

  case class Synchronize(deployment: Deployment)

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
    case SynchronizeAll =>
      implicit val timeout = PersistenceActor.timeout
      offLoad(actorFor(PersistenceActor) ? PersistenceActor.All(classOf[Deployment])) match {
        case deployments: List[_] => synchronize(deployments.asInstanceOf[List[Deployment]])
        case any => error(InternalServerError(any))
      }
    case Synchronize(deployment) => synchronize(deployment :: Nil)
  }

  private def synchronize(deployments: List[Deployment]): Unit = {
    implicit val timeout: Timeout = ContainerDriverActor.timeout

    val deploymentRoutes = offLoad(actorFor(RouterDriverActor) ? RouterDriverActor.All).asInstanceOf[DeploymentRoutes]
    val containerServices = offLoad(actorFor(ContainerDriverActor) ? ContainerDriverActor.All).asInstanceOf[List[ContainerService]]

    deployments.filterNot(withError).foreach(synchronize(containerServices, deploymentRoutes))
  }

  private def withError(deployment: Deployment): Boolean = {
    lazy val now = OffsetDateTime.now()
    lazy val config = ConfigFactory.load()
    lazy val deploymentTimeout = config.getInt("deployment.timeout.ready-for-deployment")
    lazy val undeploymentTimeout = config.getInt("deployment.timeout.ready-for-undeployment")

    def handleTimeout(service: DeploymentService) = {
      val notification = DeploymentServiceError(deployment, service)
      exception(notification)
      actorFor(PersistenceActor) ! PersistenceActor.Update(deployment.copy(clusters = deployment.clusters.map(cluster => cluster.copy(services = cluster.services.map({ s =>
        if (s.breed.name == service.breed.name) {
          s.copy(state = Error(notification))
        } else s
      })))))
      true
    }

    deployment.clusters.flatMap(_.services).exists({ service =>
      service.state match {
        case ReadyForDeployment(startedAt) =>
          if (now.minus(deploymentTimeout, ChronoUnit.SECONDS).isAfter(startedAt)) handleTimeout(service) else false

        case ReadyForUndeployment(startedAt) =>
          if (now.minus(undeploymentTimeout, ChronoUnit.SECONDS).isAfter(startedAt)) handleTimeout(service) else false

        case state: Error => true
        case _ => false
      }
    })
  }

  private def synchronize(containerServices: List[ContainerService], deploymentRoutes: DeploymentRoutes): (Deployment => Unit) = { (deployment: Deployment) =>
    val processedClusters = deployment.clusters.map { deploymentCluster =>
      val processedServices = deploymentCluster.services.map { deploymentService =>
        deploymentService.state match {
          case ReadyForDeployment(_) =>
            readyForDeployment(deployment, deploymentCluster, deploymentService, containerServices, deploymentRoutes.clusterRoutes)

          case Deployed(_) =>
            deployed(deployment, deploymentCluster, deploymentService, containerServices, deploymentRoutes.clusterRoutes)

          case ReadyForUndeployment(_) =>
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
      DeploymentServer(server.id, server.host, ports.toMap, server.deployed)
    }

    containerService(deployment, deploymentService, containerServices) match {
      case None =>
        if (deploymentService.breed.dependencies.forall({ case (n, d) =>
          deployment.clusters.flatMap(_.services).find(s => s.breed.name == d.name) match {
            case None => false
            case Some(service) => service.state.isInstanceOf[DeploymentService.Deployed]
          }
        })) {
          actorFor(ContainerDriverActor) ! ContainerDriverActor.Deploy(deployment, deploymentCluster, deploymentService, update = false)
        }

        ProcessedService(Processed.Ignore, deploymentService)

      case Some(cs) =>
        if (!matchingScale(deploymentService, cs)) {
          actorFor(ContainerDriverActor) ! ContainerDriverActor.Deploy(deployment, deploymentCluster, deploymentService, update = true)
          ProcessedService(Processed.Ignore, deploymentService)
        } else if (!matchingServers(deploymentService, cs)) {
          ProcessedService(Processed.Persist, deploymentService.copy(servers = cs.servers.map(convert)))
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
        if (!matchingServers(deploymentService, cs) || !matchingScale(deploymentService, cs)) {
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
    if (deploymentService.breed.ports.forall({ port => clusterRouteService(deployment, deploymentCluster, deploymentService, port, routes).isEmpty })) {
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

  private def matchingServers(deploymentService: DeploymentService, containerService: ContainerService) =
    deploymentService.servers.size == containerService.servers.size && deploymentService.servers.forall(server => containerService.servers.exists(_.host == server.host) && server.deployed)

  private def matchingScale(deploymentService: DeploymentService, containerService: ContainerService) =
    containerService.servers.size == deploymentService.scale.get.instances && containerService.scale.cpu == deploymentService.scale.get.cpu && containerService.scale.memory == deploymentService.scale.get.memory

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
      val dc = deploymentCluster.copy(services = processedServices.filter(_.state != Processed.RemoveFromPersistence).map(_.service))
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
        ports.foreach(port => actorFor(RouterDriverActor) ! RouterDriverActor.Create(deployment, cluster, port, update = clusterRoutes.exists(_.matching(deployment, deploymentCluster, port))))
      else
        ports.foreach(port => actorFor(RouterDriverActor) ! RouterDriverActor.Remove(deployment, cluster, port))
    }

    processedCluster
  }

  private def processClusterResults(deployment: Deployment, processedClusters: List[ProcessedCluster], routes: List[EndpointRoute]) = {
    if (processedClusters.exists(pc => pc.state == Processed.Persist || pc.state == Processed.RemoveFromPersistence)) {

      val clusters = processedClusters.filter(_.state != Processed.RemoveFromPersistence).map(_.cluster)

      val parameters = (deployment.parameters ++ processedClusters.filter(_.state == Processed.Persist).map(_.cluster).flatMap({ cluster =>
        cluster.services.map(_.breed).flatMap(_.ports).map({ port =>
          Trait.Name(Some(cluster.name), Some(Trait.Name.Group.Ports), port.name.value) -> cluster.routes.get(port.value.get).get
        })
      }).toMap).filter({
        case (Trait.Name(Some(scope), _, _), _) => clusters.exists(_.name == scope)
        case _ => true
      })

      val d = updateEndpoints(routes)(deployment.copy(clusters = clusters, parameters = parameters))

      if (d.clusters.isEmpty)
        actorFor(PersistenceActor) ! PersistenceActor.Delete(d.name, classOf[Deployment])
      else
        actorFor(PersistenceActor) ! PersistenceActor.Update(d)
    }
  }

  private def updateEndpoints(routes: List[EndpointRoute]): (Deployment => Deployment) = { deployment: Deployment =>
    def process(port: Port, number: Int): Boolean = {
      (deployment.clusters.find(_.name == port.name.scope.get), routes.find(_.matching(deployment, port))) match {
        case (None, Some(_)) =>
          actorFor(RouterDriverActor) ! RouterDriverActor.RemoveEndpoint(deployment, port)
          false

        case (Some(cluster), None) =>
          cluster.routes.get(number).foreach(_ => actorFor(RouterDriverActor) ! RouterDriverActor.CreateEndpoint(deployment, port, update = false))
          true

        case (Some(cluster), Some(route)) if route.services.flatMap(_.servers).count(_ => true) == 0 =>
          cluster.routes.get(number).foreach(_ => actorFor(RouterDriverActor) ! RouterDriverActor.CreateEndpoint(deployment, port, update = true))
          true

        case _ => true
      }
    }

    deployment.copy(endpoints = deployment.endpoints.filter({ port => {
      port match {
        case TcpPort(Trait.Name(Some(scope), Some(group), value), None, Some(number), Trait.Direction.Out) => process(port, number)
        case HttpPort(Trait.Name(Some(scope), Some(group), value), None, Some(number), Trait.Direction.Out) => process(port, number)
        case _ => false
      }
    }
    }))
  }
}
