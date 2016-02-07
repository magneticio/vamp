package io.vamp.operation.deployment

import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.akka.IoC._
import io.vamp.container_driver.{ ContainerDriverActor, ContainerInstance, ContainerService }
import io.vamp.model.artifact.DeploymentService.State.Intention
import io.vamp.model.artifact.DeploymentService.State.Step.{ Done, Update }
import io.vamp.model.artifact._
import io.vamp.model.event.Event
import io.vamp.model.resolver.DeploymentTraitResolver
import io.vamp.operation.notification.OperationNotificationProvider
import io.vamp.persistence.db.{ ArtifactPaginationSupport, PersistenceActor }
import io.vamp.persistence.operation.{ DeploymentPersistence, DeploymentServiceEnvironmentVariables, DeploymentServiceInstances, DeploymentServiceState }
import io.vamp.pulse.PulseActor.Publish
import io.vamp.pulse.{ PulseActor, PulseEventTags }

import scala.language.postfixOps

object SingleDeploymentSynchronizationActor {

  case class Synchronize(deployment: Deployment, containerServices: List[ContainerService])

}

class SingleDeploymentSynchronizationActor extends ArtifactPaginationSupport with CommonSupportForActors with DeploymentTraitResolver with OperationNotificationProvider {

  import DeploymentPersistence._
  import PulseEventTags.DeploymentSynchronization._
  import SingleDeploymentSynchronizationActor._

  def receive: Receive = {
    case Synchronize(deployment, containerServices) ⇒ synchronize(deployment, containerServices)
    case _ ⇒
  }

  private def synchronize(deployment: Deployment, containerServices: List[ContainerService]) = {
    deployment.clusters.foreach { cluster ⇒
      cluster.services.foreach { service ⇒
        service.state.intention match {
          case Intention.Deploy if service.state.isDone ⇒ redeployIfNeeded(deployment, cluster, service, containerServices)
          case Intention.Deploy                         ⇒ deploy(deployment, cluster, service, containerServices)
          case Intention.Undeploy                       ⇒ undeploy(deployment, cluster, service, containerServices)
          case _                                        ⇒
        }
      }
    }
  }

  private def deploy(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containerServices: List[ContainerService]) = {

    def deployTo(update: Boolean) = actorFor[ContainerDriverActor] ! ContainerDriverActor.Deploy(deployment, deploymentCluster, deploymentService, update = update)

    def convert(server: ContainerInstance): DeploymentInstance = {
      val ports = deploymentService.breed.ports.map(_.name) zip server.ports
      DeploymentInstance(server.name, server.host, ports.toMap, server.deployed)
    }

    containerService(deployment, deploymentService, containerServices) match {
      case None ⇒
        if (hasDependenciesDeployed(deployment, deploymentCluster, deploymentService)) {
          if (hasResolvedEnvironmentVariables(deployment, deploymentCluster, deploymentService))
            deployTo(update = false)
          else
            resolveEnvironmentVariables(deployment, deploymentCluster, deploymentService)
        }

      case Some(cs) ⇒
        if (!matchingScale(deploymentService, cs))
          deployTo(update = true)
        else if (!matchingServers(deploymentService, cs)) {
          persist(DeploymentServiceInstances(serviceArtifactName(deployment, deploymentCluster, deploymentService), cs.instances.map(convert)))
          publishUpdate(deployment, deploymentCluster)
        } else {
          persist(DeploymentServiceState(serviceArtifactName(deployment, deploymentCluster, deploymentService), deploymentService.state.copy(step = Done())))
        }
    }
  }

  private def hasDependenciesDeployed(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService) = {
    deploymentService.breed.dependencies.forall {
      case (n, d) ⇒
        deployment.clusters.exists { cluster ⇒
          cluster.services.find(s ⇒ s.breed.name == d.name) match {
            case None          ⇒ false
            case Some(service) ⇒ service.state.isDeployed && service.breed.ports.forall { port ⇒ cluster.portMapping.getOrElse(port.name, 0) > 0 }
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
          else
            Nil
        case _ ⇒ Nil
      }) ++ service.environmentVariables).map(ev ⇒ ev.name -> ev).toMap.values.toList

    val environmentVariables = local.map { ev ⇒
      ev.copy(interpolated = if (ev.interpolated.isEmpty) Some(resolve(ev.value.getOrElse(""), valueFor(deployment, Some(service)))) else ev.interpolated)
    }

    persist(DeploymentServiceEnvironmentVariables(serviceArtifactName(deployment, cluster, service), environmentVariables))
  }

  private def redeployIfNeeded(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containerServices: List[ContainerService]) = {

    def redeploy() = {
      persist(DeploymentServiceState(serviceArtifactName(deployment, deploymentCluster, deploymentService), deploymentService.state.copy(step = Update())))
      deploy(deployment, deploymentCluster, deploymentService, containerServices)
      publishUpdate(deployment, deploymentCluster)
    }

    containerService(deployment, deploymentService, containerServices) match {
      case None     ⇒ redeploy()
      case Some(cs) ⇒ if (!matchingServers(deploymentService, cs) || !matchingScale(deploymentService, cs)) redeploy()
    }
  }

  private def undeploy(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containerServices: List[ContainerService]) = {
    containerService(deployment, deploymentService, containerServices) match {
      case Some(cs) ⇒
        actorFor[ContainerDriverActor] ! ContainerDriverActor.Undeploy(deployment, deploymentService)
        persist(DeploymentServiceState(serviceArtifactName(deployment, deploymentCluster, deploymentService), deploymentService.state.copy(step = Update())))
      case _ ⇒
        persist(DeploymentServiceState(serviceArtifactName(deployment, deploymentCluster, deploymentService), deploymentService.state.copy(step = Done())))
        publishDelete(deployment, deploymentCluster)
    }
  }

  private def containerService(deployment: Deployment, deploymentService: DeploymentService, containerServices: List[ContainerService]): Option[ContainerService] = {
    containerServices.find(_.matching(deployment, deploymentService.breed))
  }

  private def matchingServers(deploymentService: DeploymentService, containerService: ContainerService) = {
    deploymentService.instances.size == containerService.instances.size &&
      deploymentService.instances.forall { server ⇒
        server.deployed && (containerService.instances.find(_.name == server.name) match {
          case None                  ⇒ false
          case Some(containerServer) ⇒ server.ports.size == containerServer.ports.size && server.ports.values.forall(port ⇒ containerServer.ports.contains(port))
        })
      }
  }

  private def matchingScale(deploymentService: DeploymentService, containerService: ContainerService) = {
    containerService.instances.size == deploymentService.scale.get.instances && containerService.scale.cpu == deploymentService.scale.get.cpu && containerService.scale.memory == deploymentService.scale.get.memory
  }

  private def publishUpdate(deployment: Deployment, cluster: DeploymentCluster) = actorFor[PulseActor] ! Publish(Event(tags(updateTag, deployment, cluster), deployment -> cluster), publishEventValue = false)

  private def publishDelete(deployment: Deployment, cluster: DeploymentCluster) = actorFor[PulseActor] ! Publish(Event(tags(deleteTag, deployment, cluster), deployment -> cluster), publishEventValue = false)

  private def tags(tag: String, deployment: Deployment, cluster: DeploymentCluster) = Set(s"deployments${Event.tagDelimiter}${deployment.name}", s"clusters${Event.tagDelimiter}${cluster.name}", tag)

  private def persist(artifact: Artifact) = actorFor[PersistenceActor] ! PersistenceActor.Update(artifact)
}
