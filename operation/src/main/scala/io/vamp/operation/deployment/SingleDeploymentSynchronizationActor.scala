package io.vamp.operation.deployment

import com.typesafe.scalalogging.LazyLogging
import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.akka.IoC._
import io.vamp.container_driver.{ ContainerDriverActor, ContainerInstance, ContainerService, Containers }
import io.vamp.model.artifact.DeploymentService.Status.Intention
import io.vamp.model.artifact.DeploymentService.Status.Phase.{ Done, Initiated, Updating }
import io.vamp.model.artifact._
import io.vamp.model.event.Event
import io.vamp.model.resolver.DeploymentValueResolver
import io.vamp.operation.notification.OperationNotificationProvider
import io.vamp.persistence.{ ArtifactPaginationSupport, PersistenceActor }
import io.vamp.pulse.PulseActor.Publish
import io.vamp.pulse.{ PulseActor, PulseEventTags }

object SingleDeploymentSynchronizationActor {

  case class Synchronize(containerService: ContainerService)

}

class SingleDeploymentSynchronizationActor extends DeploymentGatewayOperation with ArtifactPaginationSupport with CommonSupportForActors with DeploymentValueResolver with OperationNotificationProvider with LazyLogging {

  import DeploymentSynchronizationActor._
  import PersistenceActor._
  import PulseEventTags.Deployments._

  def receive: Receive = {
    case SingleDeploymentSynchronizationActor.Synchronize(containerService) ⇒ synchronize(containerService)
    case _ ⇒
  }

  private def synchronize(containerService: ContainerService): Unit = {
    log.info(s"[SingleDeploymentSynchronizationActor] Deployment Synchronization started for ${containerService.deployment.name}")
    containerService.deployment.clusters.find { cluster ⇒ cluster.services.exists(_.breed.name == containerService.service.breed.name) } match {
      case Some(cluster) ⇒
        val service = containerService.service
        val deployment = containerService.deployment
        service.status.intention match {
          case Intention.Deployment   ⇒ deploy(deployment, cluster, service, containerService)
          case Intention.Undeployment ⇒ undeploy(deployment, cluster, service, containerService.containers)
          case _                      ⇒
        }
      case _ ⇒
    }
  }

  private def deploy(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containerService: ContainerService): Unit = containerService.containers match {
    case None ⇒
      if (hasDependenciesDeployed(deployment, deploymentCluster, deploymentService)) {
        if (hasResolvedEnvironmentVariables(deployment, deploymentCluster, deploymentService))
          redeploy(deployment, deploymentCluster, deploymentService, containerService, update = false)
        else
          resolveEnvironmentVariables(deployment, deploymentCluster, deploymentService)
      }

    case Some(cs) ⇒
      if (!hasResolvedEnvironmentVariables(deployment, deploymentCluster, deploymentService))
        resolveEnvironmentVariables(deployment, deploymentCluster, deploymentService)

      else if (!matchingDeployable(containerService) || !matchingPorts(containerService) ||
        !matchingEnvironmentVariables(containerService) || !matchingScale(deploymentService, cs) || !matchingHealth(containerService))
        redeploy(deployment, deploymentCluster, deploymentService, containerService, update = true)

      else if (!matchingServers(deployment, deploymentService, cs))
        actorFor[PersistenceActor] ! UpdateDeploymentServiceInstances(deployment, deploymentCluster, deploymentService, cs.instances.map(convert(deploymentService, _)))

      else if (!matchingServiceHealth(deploymentService.health, containerService.health)) {
        val serviceHealth = containerService.health.getOrElse(deploymentService.health.get)

        actorFor[PersistenceActor] ! UpdateDeploymentServiceHealth(
          deployment,
          deploymentCluster,
          deploymentService,
          serviceHealth
        )
      }
      else if (!deploymentService.status.isDone) {
        actorFor[PersistenceActor] ! UpdateDeploymentServiceStatus(deployment, deploymentCluster, deploymentService, deploymentService.status.copy(phase = Done()))
        publishDeployed(deployment, deploymentCluster, deploymentService)
      }
  }

  private def convert(deploymentService: DeploymentService, server: ContainerInstance): Instance = {
    val ports = {
      log.debug(s"For ${server.name} breed.ports: ${deploymentService.breed.ports} matched to server ports: ${server.ports}")
      deploymentService.breed.ports.map(_.name) zip server.ports
    }
    Instance(server.name, server.host, ports.toMap, server.deployed)
  }

  private def redeploy(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containerService: ContainerService, update: Boolean): Unit = {
    if (update && deploymentService.status.phase.isInstanceOf[Initiated])
      publishRedeploy(deployment, deploymentCluster, deploymentService)

    logger.info("SingleDeploymentSynchronization redeploy deployment {} with network: {} is update {}", deployment.name, deploymentService.network.getOrElse("Empty"), update.toString)

    actorFor[PersistenceActor] ! UpdateDeploymentServiceStatus(deployment, deploymentCluster, deploymentService, deploymentService.status.copy(phase = Updating()))
    actorFor[ContainerDriverActor] ! ContainerDriverActor.Deploy(deployment, deploymentCluster, deploymentService, update = update)
  }

  private def matchingScale(deploymentService: DeploymentService, containers: Containers) = {
    val cpu = if (checkCpu()) containers.scale.cpu == deploymentService.scale.get.cpu else true
    val memory = if (checkMemory()) containers.scale.memory == deploymentService.scale.get.memory else true
    val instances = if (checkInstances()) containers.instances.size == deploymentService.scale.get.instances else true

    instances && cpu && memory
  }

  private def matchingDeployable(containerService: ContainerService): Boolean = {
    val matches = !checkDeployable() || containerService.equality.deployable
    if (!matches) log.info(s"[${containerService.deployment.name}/${containerService.service.breed.name}] matching deployable: false")
    matches
  }

  private def matchingPorts(containerService: ContainerService): Boolean = {
    val matches = !checkPorts() || containerService.equality.ports
    if (!matches) log.info(s"[${containerService.deployment.name}/${containerService.service.breed.name}] matching ports: false")
    matches
  }

  private def matchingEnvironmentVariables(containerService: ContainerService): Boolean = {
    val matches = !checkEnvironmentVariables() || containerService.equality.environmentVariables
    if (!matches) log.info(s"[${containerService.deployment.name}/${containerService.service.breed.name}] matching environment variables: false")
    matches
  }

  private def matchingHealth(containerService: ContainerService): Boolean = {
    val matches = !checkHealth() || containerService.equality.health
    if (!matches) log.info(s"[${containerService.deployment.name}/${containerService.service.breed.name}] matching health: false")
    matches
  }

  private def matchingServers(deployment: Deployment, deploymentService: DeploymentService, containers: Containers): Boolean = {
    val matches = deploymentService.instances.size == containers.instances.size &&
      deploymentService.instances.forall { server ⇒
        server.deployed && (containers.instances.find(_.name == server.name) match {
          case None                  ⇒ false
          case Some(containerServer) ⇒ server.ports.size == containerServer.ports.size && server.ports.values.forall(port ⇒ containerServer.ports.contains(port))
        })
      }
    if (!matches) log.info(s"[${deployment.name}/${deploymentService.breed.name}] matching servers: false")
    matches
  }

  private def matchingServiceHealth(deploymentHealth: Option[Health], serviceHealth: Option[Health]): Boolean = {
    (for {
      deploymentServiceHealth ← deploymentHealth
      containerServiceHealth ← serviceHealth
    } yield deploymentServiceHealth == containerServiceHealth).getOrElse {
      deploymentHealth.isEmpty && serviceHealth.isEmpty
    }
  }

  private def hasDependenciesDeployed(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService) = {
    deploymentService.breed.dependencies.forall {
      case (n, d) ⇒
        deployment.clusters.exists { cluster ⇒
          cluster.services.find(s ⇒ matchDependency(d)(s.breed)) match {
            case None ⇒ false
            case Some(service) ⇒ service.status.isDeployed && service.breed.ports.forall {
              port ⇒ cluster.serviceBy(port.name).isDefined
            }
          }
        }
    }
  }

  private def hasResolvedEnvironmentVariables(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    service.breed.environmentVariables.count(_ ⇒ true) <= service.environmentVariables.count(_ ⇒ true) && service.environmentVariables.forall(_.interpolated.isDefined)
  }

  private def resolveEnvironmentVariables(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Unit = {
    val clusterEnvironmentVariables = resolveEnvironmentVariables(deployment, cluster :: Nil)

    val local = (service.breed.environmentVariables.filter(_.value.isDefined) ++
      clusterEnvironmentVariables.flatMap(ev ⇒ TraitReference.referenceFor(ev.name) match {
        case Some(TraitReference(c, g, n)) ⇒
          if (g == TraitReference.groupFor(TraitReference.EnvironmentVariables) && ev.interpolated.isDefined && cluster.name == c && service.breed.environmentVariables.exists(_.name == n))
            ev.copy(name = n, alias = None) :: Nil
          else Nil
        case _ ⇒ Nil
      }) ++ service.environmentVariables).map(ev ⇒ ev.name → ev).toMap.values.toList

    val environmentVariables = local.map { ev ⇒
      ev.copy(interpolated = if (ev.interpolated.isEmpty) Some(resolve(ev.value.getOrElse(""), valueForWithDependencyReplacement(deployment, service))) else ev.interpolated)
    }

    actorFor[PersistenceActor] ! UpdateDeploymentServiceEnvironmentVariables(deployment, cluster, service, environmentVariables)
  }

  private def undeploy(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containers: Option[Containers]): Unit = {
    containers match {
      case Some(_) ⇒
        actorFor[ContainerDriverActor] ! ContainerDriverActor.Undeploy(deployment, deploymentCluster, deploymentService)
        actorFor[PersistenceActor] ! UpdateDeploymentServiceStatus(deployment, deploymentCluster, deploymentService, deploymentService.status.copy(phase = Updating()))
      case None ⇒
        actorFor[PersistenceActor] ! UpdateDeploymentServiceStatus(deployment, deploymentCluster, deploymentService, deploymentService.status.copy(phase = Done()))
        resetInternalRouteArtifacts(deployment, deploymentCluster, deploymentService)
        publishUndeployed(deployment, deploymentCluster, deploymentService)
    }
  }

  private def publishDeployed(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Unit = {
    actorFor[PulseActor] ! Publish(Event(tags(deployedTag, deployment, cluster, service), (deployment, cluster, service), `type` = deploymentEventType), publishEventValue = false)
  }

  private def publishRedeploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Unit = {
    actorFor[PulseActor] ! Publish(Event(tags(redeployTag, deployment, cluster, service), (deployment, cluster, service), `type` = deploymentEventType), publishEventValue = false)
  }

  private def publishUndeployed(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Unit = {
    actorFor[PulseActor] ! Publish(Event(tags(undeployedTag, deployment, cluster, service), (deployment, cluster), `type` = deploymentEventType), publishEventValue = false)
  }

  private def tags(tag: String, deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    Set(s"deployments${Event.tagDelimiter}${deployment.name}", s"clusters${Event.tagDelimiter}${cluster.name}", s"services${Event.tagDelimiter}${service.breed.name}", tag)
  }
}
