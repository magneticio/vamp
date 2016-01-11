package io.vamp.operation.deployment

import io.vamp.common.akka.CommonSupportForActors
import io.vamp.common.akka.IoC._
import io.vamp.container_driver.{ ContainerDriverActor, ContainerInstance, ContainerService }
import io.vamp.model.artifact.DeploymentService.State.Intention
import io.vamp.model.artifact.DeploymentService.State.Step.{ Done, Initiated, Update }
import io.vamp.model.artifact._
import io.vamp.model.event.Event
import io.vamp.model.resolver.DeploymentTraitResolver
import io.vamp.operation.notification.OperationNotificationProvider
import io.vamp.persistence.db.{ ArtifactPaginationSupport, PersistenceActor }
import io.vamp.pulse.PulseActor.Publish
import io.vamp.pulse.{ PulseActor, PulseEventTags }

import scala.language.postfixOps

object SingleDeploymentSynchronizationActor {

  case class Synchronize(deployment: Deployment, containerServices: List[ContainerService])

}

class SingleDeploymentSynchronizationActor extends ArtifactPaginationSupport with CommonSupportForActors with DeploymentTraitResolver with OperationNotificationProvider {

  import PulseEventTags.DeploymentSynchronization._
  import SingleDeploymentSynchronizationActor._

  private object Processed {

    trait State

    object Persist extends State

    object ResolveEnvironmentVariables extends State

    object RemoveFromPersistence extends State

    object Ignore extends State

  }

  private case class ProcessedService(state: Processed.State, service: DeploymentService)

  private case class ProcessedCluster(state: Processed.State, cluster: DeploymentCluster, processedServices: List[ProcessedService])

  def receive: Receive = {
    case Synchronize(deployment, containerServices) ⇒ synchronize(deployment, containerServices)
    case _ ⇒
  }

  private def synchronize(deployment: Deployment, containerServices: List[ContainerService]) = {
    val processedClusters = deployment.clusters.map { deploymentCluster ⇒
      val processedServices = deploymentCluster.services.map { deploymentService ⇒
        deploymentService.state.intention match {
          case Intention.Deploy ⇒
            if (deploymentService.state.isDone)
              redeployIfNeeded(deployment, deploymentCluster, deploymentService, containerServices)
            else if (deploymentService.state.step.isInstanceOf[Initiated])
              ProcessedService(Processed.Persist, deploymentService.copy(state = deploymentService.state.copy(step = Update())))
            else
              deploy(deployment, deploymentCluster, deploymentService, containerServices)

          case Intention.Undeploy ⇒
            if (deploymentService.state.step.isInstanceOf[Initiated])
              ProcessedService(Processed.Persist, deploymentService.copy(state = deploymentService.state.copy(step = Update())))
            else
              undeploy(deployment, deploymentCluster, deploymentService, containerServices)

          case _ ⇒
            ProcessedService(Processed.Ignore, deploymentService)
        }
      }
      processServiceResults(deployment, deploymentCluster, processedServices)
    }
    processClusterResults(deployment, processedClusters)
  }

  private def deploy(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containerServices: List[ContainerService]): ProcessedService = {
    def convert(server: ContainerInstance): DeploymentInstance = {
      val ports = deploymentService.breed.ports.map(_.name) zip server.ports
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
          ProcessedService(Processed.Persist, deploymentService.copy(instances = cs.instances.map(convert)))
        } else {
          ProcessedService(Processed.Persist, deploymentService.copy(state = deploymentService.state.copy(step = Done())))
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

  private def redeployIfNeeded(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containerServices: List[ContainerService]): ProcessedService = {
    def redeploy() = {
      val ds = deploymentService.copy(state = deploymentService.state.copy(step = Update()))
      deploy(deployment, deploymentCluster, ds, containerServices)
      ProcessedService(Processed.Persist, ds)
    }

    containerService(deployment, deploymentService, containerServices) match {
      case None ⇒ redeploy()
      case Some(cs) ⇒
        if (!matchingServers(deploymentService, cs) || !matchingScale(deploymentService, cs)) {
          redeploy()
        } else {
          ProcessedService(Processed.Ignore, deploymentService)
        }
    }
  }

  private def undeploy(deployment: Deployment, deploymentCluster: DeploymentCluster, deploymentService: DeploymentService, containerServices: List[ContainerService]): ProcessedService = {
    containerService(deployment, deploymentService, containerServices) match {
      case None ⇒
        ProcessedService(Processed.RemoveFromPersistence, deploymentService)
      case Some(cs) ⇒
        actorFor[ContainerDriverActor] ! ContainerDriverActor.Undeploy(deployment, deploymentService)
        ProcessedService(Processed.Persist, deploymentService.copy(state = deploymentService.state.copy(step = Update())))
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

  private def processServiceResults(deployment: Deployment, deploymentCluster: DeploymentCluster, processedServices: List[ProcessedService]): ProcessedCluster = {

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

    processedCluster
  }

  private def processClusterResults(deployment: Deployment, processedClusters: List[ProcessedCluster]) = {
    val resolve = updateEnvironmentVariables(deployment, processedClusters.filter(_.state == Processed.ResolveEnvironmentVariables))
    val persist = processedClusters.filter(_.state == Processed.Persist).map(_.cluster)
    val remove = processedClusters.filter(_.state == Processed.RemoveFromPersistence).map(_.cluster)
    val update = persist ++ resolve

    (purgeTraits(persist, remove) andThen sendDeploymentEvents(update, remove) andThen persistDeployment(update, remove))(deployment)
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

    deployment.copy(ports = purge(deployment.ports), environmentVariables = purge(deployment.environmentVariables), hosts = purge(deployment.hosts))
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

      if (clusters.isEmpty) {
        deployment.gateways foreach { gateway ⇒ actorFor[PersistenceActor] ! PersistenceActor.Delete(gateway.name, classOf[Gateway]) }
        deployment.clusters.flatMap(_.routing) foreach { gateway ⇒ actorFor[PersistenceActor] ! PersistenceActor.Delete(gateway.name, classOf[Gateway]) }
        actorFor[PersistenceActor] ! PersistenceActor.Delete(deployment.name, classOf[Deployment])
      } else {
        actorFor[PersistenceActor] ! PersistenceActor.Update(deployment.copy(clusters = clusters))
      }
    }
  }
}
