package io.vamp.operation.deployment

import io.vamp.common.Config
import io.vamp.common.akka.IoC._
import io.vamp.common.akka.{ CommonSupportForActors, IoC }
import io.vamp.container_driver.{ ContainerDriverActor, ContainerInstance, ContainerService, Containers }
import io.vamp.model.artifact.DeploymentService.Status.Intention
import io.vamp.model.artifact.DeploymentService.Status.Phase.{ Done, Initiated, Updating }
import io.vamp.model.artifact._
import io.vamp.model.event.Event
import io.vamp.model.resolver.DeploymentValueResolver
import io.vamp.operation.gateway.GatewayActor
import io.vamp.operation.notification.OperationNotificationProvider
import io.vamp.persistence._
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import io.vamp.pulse.PulseActor.Publish
import io.vamp.pulse.{ PulseActor, PulseEventTags }

object SingleDeploymentSynchronizationActor {

  case class Synchronize(containerService: ContainerService)

}

class SingleDeploymentSynchronizationActor extends DeploymentGatewayOperation with CommonSupportForActors with DeploymentValueResolver with OperationNotificationProvider with VampJsonFormats {

  import PulseEventTags.Deployments._
  import SingleDeploymentSynchronizationActor._

  private lazy val checkCpu = Config.boolean("vamp.operation.synchronization.check.cpu")()

  private lazy val checkMemory = Config.boolean("vamp.operation.synchronization.check.memory")()

  private lazy val checkInstances = Config.boolean("vamp.operation.synchronization.check.instances")()

  private lazy val checkHealthChecks = Config.boolean("vamp.operation.synchronization.check.health-checks")()

  def receive: Receive = {
    case Synchronize(containerService) ⇒ synchronize(containerService)
    case _                             ⇒
  }

  private def synchronize(containerService: ContainerService) = {

    containerService.deployment.clusters.find { cluster ⇒ cluster.services.exists(_.breed.name == containerService.service.breed.name) } match {
      case Some(cluster) ⇒

        val service = containerService.service
        val deployment = containerService.deployment

        service.status.intention match {
          case Intention.Deployment if service.status.isDone ⇒ redeployIfNeeded(deployment, cluster, service, containerService)
          case Intention.Deployment ⇒ deploy(deployment, cluster, service, containerService)
          case Intention.Undeployment ⇒ undeploy(deployment, cluster, service, containerService.containers)
          case _ ⇒
        }

      case _ ⇒
    }
  }

  private def deploy(
    deployment:        Deployment,
    deploymentCluster: DeploymentCluster,
    deploymentService: DeploymentService,
    containerService:  ContainerService) = {

    def deployTo(update: Boolean) = actorFor[ContainerDriverActor] ! ContainerDriverActor.Deploy(deployment, deploymentCluster, deploymentService, update = update)

    def convert(server: ContainerInstance): Instance = {
      val ports = {
        log.debug(s"For ${server.name} breed.ports: ${deploymentService.breed.ports} matched to server ports: ${server.ports}")
        deploymentService.breed.ports.map(_.name) zip server.ports
      }
      Instance(server.name, server.host, ports.toMap, server.deployed)
    }

    if (deploymentService.status.phase.isInstanceOf[Initiated])
      DeploymentPersistenceOperations.updateServiceStatus(deployment, deploymentCluster, deploymentService, deploymentService.status.copy(phase = Updating()))

    containerService.containers match {
      case None ⇒
        if (hasDependenciesDeployed(deployment, deploymentCluster, deploymentService)) {
          if (hasResolvedEnvironmentVariables(deployment, deploymentCluster, deploymentService))
            deployTo(update = false)
          else
            resolveEnvironmentVariables(deployment, deploymentCluster, deploymentService)
        }

      case Some(cs) ⇒
        if (!matchingScale(deploymentService, cs) || !matchingHealthChecks(containerService)) {
          deployTo(update = true)
        }
        else if (!matchingServers(deploymentService, cs)) {
          DeploymentPersistenceOperations.updateServiceInstances(deployment, deploymentCluster, deploymentService, cs.instances.map(convert))
        }
        else if (!matchingServiceHealth(deploymentService.health, containerService.health)) {
          val serviceHealth = containerService.health.getOrElse(deploymentService.health.get)
          DeploymentPersistenceOperations.updateServiceHealth(deployment, deploymentCluster, deploymentService, serviceHealth)
        }
        else {
          DeploymentPersistenceOperations.updateServiceStatus(deployment, deploymentCluster, deploymentService, deploymentService.status.copy(phase = Done()))
          updateGateways(deployment, deploymentCluster)
          publishDeployed(deployment, deploymentCluster, deploymentService)
        }
    }
  }

  private def matchingServiceHealth(deploymentHealth: Option[Health], serviceHealth: Option[Health]): Boolean =
    (for {
      deploymentServiceHealth ← deploymentHealth
      containerServiceHealth ← serviceHealth
    } yield deploymentServiceHealth == containerServiceHealth).getOrElse {
      deploymentHealth.isEmpty && serviceHealth.isEmpty
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

    DeploymentPersistenceOperations.updateServiceEnvironmentVariables(deployment, cluster, service, environmentVariables)
  }

  private def redeployIfNeeded(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containerService: ContainerService) = {

    def redeploy() = {
      DeploymentPersistenceOperations.updateServiceStatus(deployment, deploymentCluster, deploymentService, deploymentService.status.copy(phase = Updating()))
      publishRedeploy(deployment, deploymentCluster, deploymentService)
      deploy(deployment, deploymentCluster, deploymentService, containerService)
    }

    containerService.containers match {
      case None ⇒ redeploy()
      case Some(cs) ⇒ if (!matchingServers(deploymentService, cs) ||
        !matchingScale(deploymentService, cs) ||
        !matchingHealthChecks(containerService) ||
        !matchingServiceHealth(deploymentService.health, containerService.health)) redeploy()
    }
  }

  private def undeploy(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containers: Option[Containers]) = {
    containers match {
      case Some(_) ⇒
        actorFor[ContainerDriverActor] ! ContainerDriverActor.Undeploy(deployment, deploymentCluster, deploymentService)
        DeploymentPersistenceOperations.updateServiceStatus(deployment, deploymentCluster, deploymentService, deploymentService.status.copy(phase = Updating()))
      case None ⇒
        DeploymentPersistenceOperations.updateServiceStatus(deployment, deploymentCluster, deploymentService, deploymentService.status.copy(phase = Done()))
        resetInternalRouteArtifacts(deployment, deploymentCluster, deploymentService)
        publishUndeployed(deployment, deploymentCluster, deploymentService)
    }
  }

  private def matchingServers(deploymentService: DeploymentService, containers: Containers) = {
    deploymentService.instances.size == containers.instances.size &&
      deploymentService.instances.forall { server ⇒
        server.deployed && (containers.instances.find(_.name == server.name) match {
          case None                  ⇒ false
          case Some(containerServer) ⇒ server.ports.size == containerServer.ports.size && server.ports.values.forall(port ⇒ containerServer.ports.contains(port))
        })
      }
  }

  private def matchingScale(deploymentService: DeploymentService, containers: Containers) = {
    val cpu = if (checkCpu) containers.scale.cpu == deploymentService.scale.get.cpu else true
    val memory = if (checkMemory) containers.scale.memory == deploymentService.scale.get.memory else true
    val instances = if (checkInstances) containers.instances.size == deploymentService.scale.get.instances else true

    instances && cpu && memory
  }

  /**
   * Based on config value checkHealthChecks the containerService equalHealthChecks gets evaluated.
   */
  private def matchingHealthChecks(containerService: ContainerService): Boolean =
    if (checkHealthChecks) containerService.equalHealthChecks
    else true

  private def updateGateways(deployment: Deployment, cluster: DeploymentCluster) = cluster.gateways.foreach { gateway ⇒
    IoC.actorFor[GatewayActor] ! GatewayActor.PromoteInternal(gateway)
  }

  private def publishDeployed(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    actorFor[PulseActor] ! Publish(Event(tags(deployedTag, deployment, cluster, service), (deployment, cluster, service), `type` = deploymentEventType), publishEventValue = false)
  }

  private def publishRedeploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    actorFor[PulseActor] ! Publish(Event(tags(redeployTag, deployment, cluster, service), (deployment, cluster, service), `type` = deploymentEventType), publishEventValue = false)
  }

  private def publishUndeployed(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    actorFor[PulseActor] ! Publish(Event(tags(undeployedTag, deployment, cluster, service), (deployment, cluster), `type` = deploymentEventType), publishEventValue = false)
  }

  private def tags(tag: String, deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    Set(s"deployments${Event.tagDelimiter}${deployment.name}", s"clusters${Event.tagDelimiter}${cluster.name}", s"services${Event.tagDelimiter}${service.breed.name}", tag)
  }
}
