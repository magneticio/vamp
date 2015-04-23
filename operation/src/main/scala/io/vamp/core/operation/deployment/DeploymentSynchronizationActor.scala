package io.vamp.core.operation.deployment

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import _root_.io.vamp.common.akka._
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.core.container_driver.{ContainerDriverActor, ContainerServer, ContainerService}
import io.vamp.core.model.artifact.DeploymentService._
import io.vamp.core.model.artifact._
import io.vamp.core.model.resolver.DeploymentTraitResolver
import io.vamp.core.operation.deployment.DeploymentSynchronizationActor.{Synchronize, SynchronizeAll}
import io.vamp.core.operation.notification.{DeploymentServiceError, InternalServerError, OperationNotificationProvider}
import io.vamp.core.persistence.actor.PersistenceActor
import io.vamp.core.router_driver._

import scala.language.postfixOps

object DeploymentSynchronizationSchedulerActor extends ActorDescription {

  def props(args: Any*): Props = Props[DeploymentSynchronizationSchedulerActor]

}

class DeploymentSynchronizationSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick() = actorFor(DeploymentSynchronizationActor) ! SynchronizeAll
}

object DeploymentSynchronizationActor extends ActorDescription {

  def props(args: Any*): Props = Props[DeploymentSynchronizationActor]

  object SynchronizeAll

  case class Synchronize(deployment: Deployment)

}

class DeploymentSynchronizationActor extends Actor with DeploymentTraitResolver with ActorLogging with ActorSupport with FutureSupport with ActorExecutionContextProvider with OperationNotificationProvider {

  private object Processed {

    trait State

    object Persist extends State

    object ResolveEnvironmentVariables extends State

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
      offload(actorFor(PersistenceActor) ? PersistenceActor.All(classOf[Deployment])) match {
        case deployments: List[_] => synchronize(deployments.asInstanceOf[List[Deployment]])
        case any => error(InternalServerError(any))
      }
    case Synchronize(deployment) => synchronize(deployment :: Nil)
  }

  private def synchronize(deployments: List[Deployment]): Unit = {
    implicit val timeout: Timeout = ContainerDriverActor.timeout

    val deploymentRoutes = offload(actorFor(RouterDriverActor) ? RouterDriverActor.All).asInstanceOf[DeploymentRoutes]
    val containerServices = offload(actorFor(ContainerDriverActor) ? ContainerDriverActor.All).asInstanceOf[List[ContainerService]]

    deployments.filterNot(withError).foreach(synchronize(containerServices, deploymentRoutes))
  }

  private def withError(deployment: Deployment): Boolean = {
    lazy val now = OffsetDateTime.now()
    lazy val config = ConfigFactory.load().getConfig("vamp.core.operation.synchronization.timeout")
    lazy val deploymentTimeout = config.getInt("ready-for-deployment")
    lazy val undeploymentTimeout = config.getInt("ready-for-undeployment")

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
        dp <- deploymentService.breed.ports.map(_.number)
        sp <- server.ports
      } yield (dp, sp)
      DeploymentServer(server.name, server.host, ports.toMap, server.deployed)
    }

    containerService(deployment, deploymentService, containerServices) match {
      case None =>
        if (hasDependenciesDeployed(deployment, deploymentCluster, deploymentService)) {
          if (hasResolvedEnvironmentVariables(deployment, deploymentCluster, deploymentService)) {
            actorFor(ContainerDriverActor) ! ContainerDriverActor.Deploy(deployment, deploymentCluster, deploymentService, update = false)
            ProcessedService(Processed.Ignore, deploymentService)
          } else {
            ProcessedService(Processed.ResolveEnvironmentVariables, deploymentService)
          }
        } else {
          ProcessedService(Processed.Ignore, deploymentService)
        }

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

  private def hasDependenciesDeployed(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService) = {
    deploymentService.breed.dependencies.forall({ case (n, d) =>
      deployment.clusters.flatMap(_.services).find(s => s.breed.name == d.name) match {
        case None => false
        case Some(service) => service.state.isInstanceOf[DeploymentService.Deployed]
      }
    })
  }

  private def hasResolvedEnvironmentVariables(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService) = {
    deploymentService.breed.environmentVariables.forall({ evBreed =>
      !deployment.environmentVariables.exists(evDeployment => if (!evDeployment.interpolated) {
        TraitReference.referenceFor(evDeployment.name) match {
          case Some(TraitReference(cluster, group, name)) => cluster == deploymentCluster.name && evBreed.name == name && group == TraitReference.groupFor(TraitReference.EnvironmentVariables)
          case _ => false
        }
      } else false)
    })
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
    deploymentService.servers.size == containerService.servers.size && deploymentService.servers.forall(server => containerService.servers.exists(_.name == server.name) && server.deployed)

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

  private def matching(deploymentService: DeploymentService, routeService: RouteService) = {
    val matchingServers = deploymentService.servers.size == routeService.servers.size && deploymentService.servers.forall(server => routeService.servers.exists(_.host == server.host))

    val matchingServersWeight = deploymentService.routing.flatMap(_.weight.flatMap(w => Some(w == routeService.weight))) match {
      case None => false
      case Some(m) => m
    }

    val matchingFilters = (deploymentService.routing match {
      case None => Nil
      case Some(r) => r.filters.flatMap {
        case d: DefaultFilter => d.condition :: Nil
        case _ => Nil
      }
    }) == routeService.filters.map(_.condition)

    matchingServers && matchingServersWeight && matchingFilters
  }

  private def clusterRouteService(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, port: Port, clusterRoutes: List[ClusterRoute]): Option[RouteService] =
    clusterRoutes.find(_.matching(deployment, deploymentCluster, port)) match {
      case None => None
      case Some(route) => route.services.find(_.name == deploymentService.breed.name)
    }

  private def processServiceResults(deployment: Deployment, deploymentCluster: DeploymentCluster, clusterRoutes: List[ClusterRoute], processedServices: List[ProcessedService]): ProcessedCluster = {

    val processedCluster = if (processedServices.exists(s => s.state == Processed.Persist || s.state == Processed.RemoveFromPersistence || s.state == Processed.ResolveEnvironmentVariables)) {
      val dc = deploymentCluster.copy(services = processedServices.filter(_.state != Processed.RemoveFromPersistence).map(_.service))
      val state = if (dc.services.isEmpty) Processed.RemoveFromPersistence
      else {
        if (processedServices.exists(s => s.state == Processed.ResolveEnvironmentVariables)) Processed.ResolveEnvironmentVariables else Processed.Persist
      }
      ProcessedCluster(state, dc)
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
    val resolveEnvironmentVariables = processedClusters.filter(_.state == Processed.ResolveEnvironmentVariables).map(_.cluster)
    val persist = processedClusters.filter(_.state == Processed.Persist).map(_.cluster)
    val remove = processedClusters.filter(_.state == Processed.RemoveFromPersistence).map(_.cluster)

    (updateTraits(persist, resolveEnvironmentVariables) andThen purgeTraits(remove) andThen updateEndpoints(remove, routes) andThen persistDeployment(persist ++ resolveEnvironmentVariables, remove))(deployment)
  }

  private def updateTraits(persist: List[DeploymentCluster], resolve: List[DeploymentCluster]): (Deployment => Deployment) = { deployment: Deployment =>
    val ports = persist.flatMap({ cluster =>
      cluster.services.map(_.breed).flatMap(_.ports).map({ port =>
        Port(TraitReference(cluster.name, TraitReference.groupFor(TraitReference.Ports), port.name).toString, None, cluster.routes.get(port.number).flatMap(n => Some(n.toString)))
      })
    }).map(p => p.name -> p).toMap ++ deployment.ports.map(p => p.name -> p).toMap

    val environmentVariables = if (resolve.nonEmpty) resolveEnvironmentVariables(deployment, resolve) else deployment.environmentVariables

    deployment.copy(ports = ports.values.toList, environmentVariables = environmentVariables)
  }

  private def purgeTraits(remove: List[DeploymentCluster]): (Deployment => Deployment) = { deployment: Deployment =>
    def purge[A <: Trait](traits: List[A]): List[A] = traits.filterNot(t => TraitReference.referenceFor(t.name) match {
      case Some(TraitReference(cluster, _, _)) => remove.exists(_.name == cluster)
      case _ => true
    })

    deployment.copy(endpoints = purge(deployment.endpoints), ports = purge(deployment.ports), environmentVariables = purge(deployment.environmentVariables), constants = purge(deployment.constants), hosts = purge(deployment.hosts))
  }

  private def updateEndpoints(remove: List[DeploymentCluster], routes: List[EndpointRoute]): (Deployment => Deployment) = { deployment: Deployment =>

    routes.foreach(route => if (!deployment.endpoints.exists(_.number == route.port)) {
      actorFor(RouterDriverActor) ! RouterDriverActor.RemoveEndpoint(deployment, Port.portFor(route.port))
    })

    deployment.endpoints.foreach { port =>
      TraitReference.referenceFor(port.name) match {
        case Some(TraitReference(cluster, _, _)) =>
          (deployment.clusters.find(_.name == cluster), routes.find(_.matching(deployment, port))) match {

            case (Some(_), None) =>
              actorFor(RouterDriverActor) ! RouterDriverActor.CreateEndpoint(deployment, port, update = false)

            case (Some(_), Some(route)) if route.services.flatMap(_.servers).count(_ => true) == 0 =>
              actorFor(RouterDriverActor) ! RouterDriverActor.CreateEndpoint(deployment, port, update = true)

            case _ =>
          }
        case _ =>
      }
    }

    deployment
  }

  private def persistDeployment(persist: List[DeploymentCluster], remove: List[DeploymentCluster]): (Deployment => Unit) = { deployment: Deployment =>
    if (persist.nonEmpty || remove.nonEmpty) {
      val clusters = deployment.clusters.filterNot(cluster => remove.exists(_.name == cluster.name)).map(cluster => persist.find(_.name == cluster.name).getOrElse(cluster))

      if (clusters.isEmpty)
        actorFor(PersistenceActor) ! PersistenceActor.Delete(deployment.name, classOf[Deployment])
      else
        actorFor(PersistenceActor) ! PersistenceActor.Update(deployment.copy(clusters = clusters))
    }
  }
}
