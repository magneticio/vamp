package io.vamp.operation.deployment

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common.notification.NotificationErrorException
import io.vamp.container_driver.{ ContainerDriverActor, ContainerInstance, ContainerService }
import io.vamp.gateway_driver._
import io.vamp.gateway_driver.model.{ ClusterGateway, DeploymentGateways, EndpointGateway, GatewayService }
import io.vamp.model.artifact.DeploymentService.State.Intention
import io.vamp.model.artifact.DeploymentService.State.Step._
import io.vamp.model.artifact.DeploymentService._
import io.vamp.model.artifact._
import io.vamp.model.event.Event
import io.vamp.model.resolver.DeploymentTraitResolver
import io.vamp.operation.deployment.DeploymentSynchronizationActor.SynchronizeAll
import io.vamp.operation.notification.{ DeploymentServiceError, InternalServerError, OperationNotificationProvider }
import io.vamp.persistence.{ ArtifactPaginationSupport, PersistenceActor }
import io.vamp.pulse.{ PulseEventTags, PulseActor }
import io.vamp.pulse.PulseActor.Publish

import scala.concurrent.Future
import scala.language.postfixOps

class DeploymentSynchronizationSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick() = IoC.actorFor[DeploymentSynchronizationActor] ! SynchronizeAll
}

object DeploymentSynchronizationActor {

  object SynchronizeAll

  case class Synchronize(deployment: Deployment)
}

class DeploymentSynchronizationActor extends ArtifactPaginationSupport with CommonSupportForActors with DeploymentTraitResolver with OperationNotificationProvider {

  import DeploymentSynchronizationActor._
  import PulseEventTags.DeploymentSynchronization._

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

  private case class ProcessedCluster(state: Processed.State, cluster: DeploymentCluster, processedServices: List[ProcessedService])

  def receive: Receive = {

    case SynchronizeAll ⇒
      implicit val timeout = PersistenceActor.timeout
      allArtifacts[Deployment] map synchronize

    case Synchronize(deployment) ⇒ synchronize(deployment :: Nil)

    case _                       ⇒
  }

  private def synchronize(deployments: List[Deployment]): Future[_] = {
    implicit val timeout: Timeout = ContainerDriverActor.timeout
    val gateways = actorFor[GatewayDriverActor] ? GatewayDriverActor.GetAllAfterPurge(deployments)
    val containers = actorFor[ContainerDriverActor] ? ContainerDriverActor.All

    gateways map {
      case error: NotificationErrorException ⇒ log.error("Synchronisation not possible.")
      case deploymentRoutes: DeploymentGateways ⇒
        containers map {
          case containerServices: List[_] ⇒ deployments.filterNot(withError).foreach(synchronize(containerServices.asInstanceOf[List[ContainerService]], deploymentRoutes))
          case any                        ⇒ throwException(InternalServerError(any))
        }
      case other ⇒ throwException(InternalServerError(other))
    }
  }

  private def withError(deployment: Deployment): Boolean = {
    lazy val now = OffsetDateTime.now()
    lazy val config = ConfigFactory.load().getConfig("vamp.operation.synchronization.timeout")
    lazy val deploymentTimeout = config.getInt("ready-for-deployment")
    lazy val undeploymentTimeout = config.getInt("ready-for-undeployment")

    def handleTimeout(service: DeploymentService) = {
      val notification = DeploymentServiceError(deployment, service)
      reportException(notification)
      actorFor[PersistenceActor] ! PersistenceActor.Update(deployment.copy(clusters = deployment.clusters.map(cluster ⇒ cluster.copy(services = cluster.services.map({ s ⇒
        if (s.breed.name == service.breed.name) {
          s.copy(state = State(s.state.intention, Failure(notification)))
        } else s
      })))))
      true
    }

    deployment.clusters.flatMap(_.services).exists({ service ⇒
      service.state.intention match {
        case Intention.Deploy ⇒
          if (!service.state.isDone && now.minus(deploymentTimeout, ChronoUnit.SECONDS).isAfter(service.state.since)) handleTimeout(service) else false

        case Intention.Undeploy ⇒
          if (!service.state.isDone && now.minus(undeploymentTimeout, ChronoUnit.SECONDS).isAfter(service.state.since)) handleTimeout(service) else false

        case _ ⇒ service.state.step.isInstanceOf[Failure]
      }
    })
  }

  private def synchronize(containerServices: List[ContainerService], deploymentRoutes: DeploymentGateways): (Deployment ⇒ Unit) = { (deployment: Deployment) ⇒
    val processedClusters = deployment.clusters.map { deploymentCluster ⇒
      val processedServices = deploymentCluster.services.map { deploymentService ⇒
        deploymentService.state.intention match {
          case Intention.Deploy ⇒
            if (deploymentService.state.isDone)
              redeployIfNeeded(deployment, deploymentCluster, deploymentService, containerServices, deploymentRoutes.clusterGateways)
            else if (deploymentService.state.step.isInstanceOf[Initiated])
              ProcessedService(Processed.Persist, deploymentService.copy(state = deploymentService.state.copy(step = ContainerUpdate())))
            else
              deploy(deployment, deploymentCluster, deploymentService, containerServices, deploymentRoutes.clusterGateways)

          case Intention.Undeploy ⇒
            if (deploymentService.state.step.isInstanceOf[Initiated])
              ProcessedService(Processed.Persist, deploymentService.copy(state = deploymentService.state.copy(step = RouteUpdate())))
            else
              undeploy(deployment, deploymentCluster, deploymentService, containerServices, deploymentRoutes.clusterGateways)

          case _ ⇒
            ProcessedService(Processed.Ignore, deploymentService)
        }
      }
      processServiceResults(deployment, deploymentCluster, deploymentRoutes.clusterGateways, processedServices)
    }
    processClusterResults(deployment, processedClusters, deploymentRoutes.endpointGateways)
  }

  private def deploy(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containerServices: List[ContainerService], clusterRoutes: List[ClusterGateway]): ProcessedService = {
    def convert(server: ContainerInstance): DeploymentInstance = {
      val ports = deploymentService.breed.ports.map(_.number) zip server.ports
      DeploymentInstance(server.name, server.host, ports.toMap, server.deployed)
    }

    containerService(deployment, deploymentService, containerServices) match {
      case None ⇒
        if (hasDependenciesDeployed(deployment, deploymentCluster, deploymentService)) {
          if (hasResolvedEnvironmentVariables(deployment, deploymentCluster, deploymentService)) {
            actorFor[ContainerDriverActor] ! ContainerDriverActor.Deploy(deployment, deploymentCluster, deploymentService, update = false)
            ProcessedService(Processed.Ignore, deploymentService)
          } else {
            ProcessedService(Processed.ResolveEnvironmentVariables, deploymentService)
          }
        } else {
          ProcessedService(Processed.Ignore, deploymentService)
        }

      case Some(cs) ⇒
        if (!matchingScale(deploymentService, cs)) {
          actorFor[ContainerDriverActor] ! ContainerDriverActor.Deploy(deployment, deploymentCluster, deploymentService, update = true)
          ProcessedService(Processed.Ignore, deploymentService)
        } else if (!matchingServers(deploymentService, cs)) {
          ProcessedService(Processed.Persist, deploymentService.copy(instances = cs.instances.map(convert), state = deploymentService.state.copy(step = RouteUpdate())))
        } else {
          val ports = outOfSyncPorts(deployment, deploymentCluster, deploymentService, cs, clusterRoutes)
          if (ports.isEmpty) {
            ProcessedService(Processed.Persist, deploymentService.copy(state = deploymentService.state.copy(step = Done())))
          } else {
            ProcessedService(Processed.UpdateRoute(ports), deploymentService)
          }
        }
    }
  }

  private def hasDependenciesDeployed(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService) = {
    deploymentService.breed.dependencies.forall({
      case (n, d) ⇒
        deployment.clusters.flatMap(_.services).find(s ⇒ s.breed.name == d.name) match {
          case None          ⇒ false
          case Some(service) ⇒ service.state.isDeployed
        }
    })
  }

  private def hasResolvedEnvironmentVariables(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService) = {
    deploymentService.breed.environmentVariables.count(_ ⇒ true) <= deploymentService.environmentVariables.count(_ ⇒ true) && deploymentService.environmentVariables.forall(_.interpolated.isDefined)
  }

  private def redeployIfNeeded(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containerServices: List[ContainerService], routes: List[ClusterGateway]): ProcessedService = {
    def redeploy() = {
      val ds = deploymentService.copy(state = deploymentService.state.copy(step = ContainerUpdate()))
      deploy(deployment, deploymentCluster, ds, containerServices, routes)
      ProcessedService(Processed.Persist, ds)
    }

    containerService(deployment, deploymentService, containerServices) match {
      case None ⇒ redeploy()
      case Some(cs) ⇒
        if (!matchingServers(deploymentService, cs) || !matchingScale(deploymentService, cs)) {
          redeploy()
        } else {
          val ports = outOfSyncPorts(deployment, deploymentCluster, deploymentService, cs, routes)
          if (ports.isEmpty) {
            ProcessedService(Processed.Ignore, deploymentService)
          } else {
            redeploy()
          }
        }
    }
  }

  private def undeploy(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containerServices: List[ContainerService], routes: List[ClusterGateway]): ProcessedService = {
    if (deploymentService.breed.ports.forall({ port ⇒ clusterRouteService(deployment, deploymentCluster, deploymentService, port, routes).isEmpty })) {
      containerService(deployment, deploymentService, containerServices) match {
        case None ⇒
          ProcessedService(Processed.RemoveFromPersistence, deploymentService)
        case Some(cs) ⇒
          actorFor[ContainerDriverActor] ! ContainerDriverActor.Undeploy(deployment, deploymentService)
          ProcessedService(Processed.Persist, deploymentService.copy(state = deploymentService.state.copy(step = ContainerUpdate())))
      }
    } else {
      ProcessedService(Processed.RemoveFromRoute, deploymentService)
    }
  }

  private def containerService(deployment: Deployment, deploymentService: DeploymentService, containerServices: List[ContainerService]): Option[ContainerService] =
    containerServices.find(_.matching(deployment, deploymentService.breed))

  private def matchingServers(deploymentService: DeploymentService, containerService: ContainerService) = {
    deploymentService.instances.size == containerService.instances.size &&
      deploymentService.instances.forall { server ⇒
        server.deployed && (containerService.instances.find(_.name == server.name) match {
          case None                  ⇒ false
          case Some(containerServer) ⇒ server.ports.size == containerServer.ports.size && server.ports.values.forall(port ⇒ containerServer.ports.contains(port))
        })
      }
  }

  private def matchingScale(deploymentService: DeploymentService, containerService: ContainerService) =
    containerService.instances.size == deploymentService.scale.get.instances && containerService.scale.cpu == deploymentService.scale.get.cpu && containerService.scale.memory == deploymentService.scale.get.memory

  private def outOfSyncPorts(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containerService: ContainerService, clusterRoutes: List[ClusterGateway]): List[Port] = {
    deploymentService.breed.ports.filter { port ⇒

      clusterRouteService(deployment, deploymentCluster, deploymentService, port, clusterRoutes) match {

        case None ⇒ true

        case Some(routeService) ⇒

          lazy val matchingServers = deploymentService.instances.size == routeService.instances.size && deploymentService.instances.forall { deploymentServer ⇒
            routeService.instances.exists(routerServer ⇒ routerServer.host == deploymentServer.host && routerServer.port == deploymentServer.ports.getOrElse(port.number, 0))
          }

          lazy val matchingServersWeight = deploymentService.routing.flatMap(_.weight.flatMap(w ⇒ Some(w == routeService.weight))) match {
            case None    ⇒ false
            case Some(m) ⇒ m
          }

          lazy val matchingFilters = (deploymentService.routing match {
            case None ⇒ Nil
            case Some(r) ⇒ r.filters.flatMap {
              case d: DefaultFilter ⇒ d.condition :: Nil
              case _                ⇒ Nil
            }
          }) == routeService.filters.map(_.condition)

          !(matchingServers && matchingServersWeight && matchingFilters)
      }
    }
  }

  private def clusterRouteService(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, port: Port, clusterRoutes: List[ClusterGateway]): Option[GatewayService] =
    clusterRoutes.find(_.matching(deployment, deploymentCluster, port)) match {
      case None        ⇒ None
      case Some(route) ⇒ route.services.find(_.matching(deploymentService))
    }

  private def processServiceResults(deployment: Deployment, deploymentCluster: DeploymentCluster, clusterRoutes: List[ClusterGateway], processedServices: List[ProcessedService]): ProcessedCluster = {

    val processedCluster = if (processedServices.exists(s ⇒ s.state == Processed.Persist || s.state == Processed.RemoveFromPersistence || s.state == Processed.ResolveEnvironmentVariables)) {
      val dc = deploymentCluster.copy(services = processedServices.filter(_.state != Processed.RemoveFromPersistence).map(_.service))
      val state = if (dc.services.isEmpty) Processed.RemoveFromPersistence
      else {
        if (processedServices.exists(s ⇒ s.state == Processed.ResolveEnvironmentVariables)) Processed.ResolveEnvironmentVariables else Processed.Persist
      }
      ProcessedCluster(state, dc, processedServices)
    } else {
      ProcessedCluster(Processed.Ignore, deploymentCluster, processedServices)
    }

    val ports: Set[Port] = processedServices.flatMap(processed ⇒ processed.state match {
      case Processed.RemoveFromRoute ⇒ processed.service.breed.ports
      case Processed.UpdateRoute(p)  ⇒ p
      case _                         ⇒ Nil
    }).toSet

    if (ports.nonEmpty) {
      val cluster = processedCluster.cluster.copy(services = processedServices.filter(_.state != Processed.RemoveFromRoute).map(_.service))
      if (cluster.services.nonEmpty)
        ports.foreach(port ⇒ actorFor[GatewayDriverActor] ! GatewayDriverActor.Create(deployment, cluster, port))
      else
        ports.foreach(port ⇒ actorFor[GatewayDriverActor] ! GatewayDriverActor.Remove(deployment, cluster, port))
    }

    processedCluster
  }

  private def processClusterResults(deployment: Deployment, processedClusters: List[ProcessedCluster], routes: List[EndpointGateway]) = {
    val resolve = updateEnvironmentVariables(deployment, processedClusters.filter(_.state == Processed.ResolveEnvironmentVariables))
    val persist = processedClusters.filter(_.state == Processed.Persist).map(_.cluster)
    val remove = processedClusters.filter(_.state == Processed.RemoveFromPersistence).map(_.cluster)
    val update = persist ++ resolve

    (updatePorts(persist) andThen purgeTraits(persist, remove) andThen updateEndpoints(remove, routes) andThen sendDeploymentEvents(update, remove) andThen persistDeployment(update, remove))(deployment)
  }

  private def updatePorts(persist: List[DeploymentCluster]): (Deployment ⇒ Deployment) = { deployment: Deployment ⇒
    val ports = persist.flatMap({ cluster ⇒
      cluster.services.map(_.breed).flatMap(_.ports).map({ port ⇒
        Port(TraitReference(cluster.name, TraitReference.groupFor(TraitReference.Ports), port.name).toString, None, cluster.routes.get(port.number).flatMap(n ⇒ Some(n.toString)))
      })
    }).map(p ⇒ p.name -> p).toMap ++ deployment.ports.map(p ⇒ p.name -> p).toMap

    deployment.copy(ports = ports.values.toList)
  }

  private def updateEnvironmentVariables(deployment: Deployment, processedClusters: List[ProcessedCluster]): List[DeploymentCluster] = {
    val resolveClusters = processedClusters.map(_.cluster)
    val environmentVariables = if (resolveClusters.nonEmpty) resolveEnvironmentVariables(deployment, resolveClusters) else deployment.environmentVariables

    processedClusters.map { pc ⇒
      pc.cluster.copy(services = pc.processedServices.map { ps ⇒
        if (ps.state == Processed.ResolveEnvironmentVariables) {
          val local = (ps.service.breed.environmentVariables.filter(_.value.isDefined) ++
            environmentVariables.flatMap(ev ⇒ TraitReference.referenceFor(ev.name) match {
              case Some(TraitReference(c, g, n)) if g == TraitReference.groupFor(TraitReference.EnvironmentVariables) && ev.interpolated.isDefined && pc.cluster.name == c ⇒
                List(ev.copy(name = n, alias = None))
              case _ ⇒ Nil
            }) ++
            ps.service.environmentVariables).map(ev ⇒ ev.name -> ev).toMap.values.toList

          ps.service.copy(environmentVariables = local.map { ev ⇒
            ev.copy(interpolated = if (ev.interpolated.isEmpty) Some(resolve(ev.value.getOrElse(""), valueFor(deployment, Some(ps.service)))) else ev.interpolated)
          })
        } else ps.service
      })
    }
  }

  private def purgeTraits(persist: List[DeploymentCluster], remove: List[DeploymentCluster]): (Deployment ⇒ Deployment) = { deployment: Deployment ⇒
    def purge[A <: Trait](traits: List[A]): List[A] = traits.filterNot(t ⇒ TraitReference.referenceFor(t.name) match {
      case Some(TraitReference(cluster, group, name)) ⇒
        if (remove.exists(_.name == cluster)) true
        else persist.find(_.name == cluster) match {
          case None ⇒ false
          case Some(c) ⇒ TraitReference.groupFor(group) match {
            case Some(TraitReference.Hosts) ⇒ false
            case Some(g)                    ⇒ !c.services.flatMap(_.breed.traitsFor(g)).exists(_.name == name)
            case None                       ⇒ true
          }
        }

      case _ ⇒ true
    })

    deployment.copy(endpoints = purge(deployment.endpoints), ports = purge(deployment.ports), environmentVariables = purge(deployment.environmentVariables), hosts = purge(deployment.hosts))
  }

  private def updateEndpoints(remove: List[DeploymentCluster], routes: List[EndpointGateway]): (Deployment ⇒ Deployment) = { deployment: Deployment ⇒

    routes.filter(_.matching(deployment, None)).foreach(route ⇒ if (!deployment.endpoints.exists(_.number == route.port)) {
      actorFor[GatewayDriverActor] ! GatewayDriverActor.RemoveEndpoint(deployment, Port.portFor(route.port))
    })

    deployment.endpoints.foreach { port ⇒
      TraitReference.referenceFor(port.name).map(_.cluster).foreach { clusterName ⇒
        deployment.clusters.find(_.name == clusterName).foreach { cluster ⇒
          if (cluster.services.forall(_.state.isDeployed)) routes.find(_.matching(deployment, Some(port))) match {

            case None ⇒
              actorFor[GatewayDriverActor] ! GatewayDriverActor.CreateEndpoint(deployment, port)

            case Some(route) if route.services.flatMap(_.instances).count(_ ⇒ true) == 0 ⇒
              actorFor[GatewayDriverActor] ! GatewayDriverActor.CreateEndpoint(deployment, port)

            case _ ⇒
          }
        }
      }
    }

    deployment
  }

  private def sendDeploymentEvents(persist: List[DeploymentCluster], remove: List[DeploymentCluster]): (Deployment ⇒ Deployment) = { deployment: Deployment ⇒

    def tags(tag: String, cluster: DeploymentCluster) = Set(s"deployments${Event.tagDelimiter}${deployment.name}", s"clusters${Event.tagDelimiter}${cluster.name}", tag)

    remove.foreach(cluster ⇒ actorFor[PulseActor] ! Publish(Event(tags(deleteTag, cluster), deployment -> cluster), publishEventValue = false))

    persist.filter(_.services.forall(_.state.isDone)).foreach(cluster ⇒ actorFor[PulseActor] ! Publish(Event(tags(updateTag, cluster), deployment -> cluster), publishEventValue = false))

    deployment
  }

  private def persistDeployment(persist: List[DeploymentCluster], remove: List[DeploymentCluster]): (Deployment ⇒ Unit) = { deployment: Deployment ⇒
    if (persist.nonEmpty || remove.nonEmpty) {
      val clusters = deployment.clusters.filterNot(cluster ⇒ remove.exists(_.name == cluster.name)).map(cluster ⇒ persist.find(_.name == cluster.name).getOrElse(cluster))

      if (clusters.isEmpty)
        actorFor[PersistenceActor] ! PersistenceActor.Delete(deployment.name, classOf[Deployment])
      else
        actorFor[PersistenceActor] ! PersistenceActor.Update(deployment.copy(clusters = clusters))
    }
  }
}
