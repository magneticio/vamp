package io.magnetic.vamp_core.operation.deployment

import java.util.UUID

import _root_.io.magnetic.vamp_common.akka._
import _root_.io.magnetic.vamp_core.model.artifact.DeploymentService.ReadyForDeployment
import _root_.io.magnetic.vamp_core.model.artifact._
import _root_.io.magnetic.vamp_core.operation.deployment.DeploymentActor.{Create, Delete, DeploymentMessages, Update}
import _root_.io.magnetic.vamp_core.operation.deployment.DeploymentSynchronizationActor.Synchronize
import _root_.io.magnetic.vamp_core.operation.notification.{NonUniqueBreedReferenceError, OperationNotificationProvider, UnresolvedDependencyError, UnsupportedDeploymentRequest}
import _root_.io.magnetic.vamp_core.persistence.PersistenceActor
import _root_.io.magnetic.vamp_core.persistence.notification.ArtifactNotFound
import _root_.io.magnetic.vamp_core.persistence.store.InMemoryStoreProvider
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.language.existentials
import scala.reflect._

object DeploymentActor extends ActorDescription {

  def props(args: Any*): Props = Props[DeploymentActor]

  trait DeploymentMessages

  case class Create(blueprint: Blueprint) extends DeploymentMessages

  case class Update(name: String, blueprint: Blueprint) extends DeploymentMessages

  case class Delete(name: String, blueprint: Option[Blueprint]) extends DeploymentMessages

}

class DeploymentActor extends Actor with ActorLogging with ActorSupport with ReplyActor with FutureSupport with InMemoryStoreProvider with ActorExecutionContextProvider with OperationNotificationProvider {

  private def uuid = UUID.randomUUID.toString

  override protected def requestType: Class[_] = classOf[DeploymentMessages]

  override protected def errorRequest(request: Any): RequestError = UnsupportedDeploymentRequest(request)

  def reply(request: Any) = try {
    request match {
      case Create(blueprint) => merge(Deployment(uuid, List(), Map(), Map()), asDefaultBlueprint(blueprint))
      case Update(name, blueprint) => merge(artifactFor[Deployment](name), asDefaultBlueprint(blueprint))
      case Delete(name, blueprint) => slice(artifactFor[Deployment](name), blueprint.flatMap { bp =>
        Some(asDefaultBlueprint(bp))
      })
      case _ => exception(errorRequest(request))
    }
  } catch {
    case e: Exception => e
  }

  private def artifactFor[T <: Artifact : ClassTag](name: String): T = {
    implicit val timeout = PersistenceActor.timeout
    offLoad(actorFor(PersistenceActor) ? PersistenceActor.Read(name, classTag[T].runtimeClass.asInstanceOf[Class[Artifact]])) match {
      case Some(artifact: T) => artifact
      case _ => error(ArtifactNotFound(name, classTag[T].runtimeClass))
    }
  }

  private def asDefaultBlueprint(blueprint: Blueprint): DefaultBlueprint = blueprint match {
    case defaultBlueprint: DefaultBlueprint => defaultBlueprint
    case reference: BlueprintReference => artifactFor[Blueprint](reference.name).asInstanceOf[DefaultBlueprint]
  }

  private def merge(deployment: Deployment, blueprint: DefaultBlueprint): Any = {
    val clusters = mergeClusters(deployment, blueprint)
    val endpoints = blueprint.endpoints ++ deployment.endpoints
    val parameters = blueprint.parameters ++ deployment.parameters

    commit(Deployment(deployment.name, clusters, endpoints, parameters))
  }

  private def mergeClusters(deployment: Deployment, blueprint: DefaultBlueprint): List[DeploymentCluster] = {
    val deploymentClusters = deployment.clusters.filter(cluster => blueprint.clusters.find(_.name == cluster.name).isEmpty)

    val blueprintClusters = blueprint.clusters.map { cluster =>
      deployment.clusters.find(_.name == cluster.name) match {
        case None => DeploymentCluster(cluster.name, mergeServices(None, cluster), cluster.sla)
        case Some(deploymentCluster) => deploymentCluster.copy(services = mergeServices(Some(deploymentCluster), cluster))
      }
    }

    deploymentClusters ++ blueprintClusters
  }

  private def mergeServices(deploymentCluster: Option[DeploymentCluster], cluster: Cluster): List[DeploymentService] = {

    def asDeploymentService(service: Service) = {
      val breed = service.breed match {
        case b: DefaultBreed => b
        case b: Breed => artifactFor[Breed](b.name).asInstanceOf[DefaultBreed]
      }

      val scale = service.scale.flatMap {
        case scale: DefaultScale => Some(scale)
        case scale: Scale => Some(artifactFor[Scale](scale.name).asInstanceOf[DefaultScale])
      }

      val routing = service.routing.flatMap {
        case routing: DefaultRouting => Some(routing)
        case routing: Routing => Some(artifactFor[Routing](routing.name).asInstanceOf[DefaultRouting])
      }

      DeploymentService(ReadyForDeployment(), breed, scale, Nil, routing)
    }

    deploymentCluster match {
      case None => cluster.services.map {
        asDeploymentService
      }
      case Some(deployment) => deployment.services ++ cluster.services.filter(service => deployment.services.find(_.breed.name == service.breed.name).isEmpty).map {
        asDeploymentService
      }
    }
  }

  private def slice(deployment: Deployment, blueprint: Option[DefaultBlueprint]): Any = blueprint match {
    case None =>
    // TODO set state => for removal
    // actorFor(DeploymentPipeline) ! DeploymentPipeline.Synchronize(d)
    // delete afterwards

    case Some(bp) =>
      // TODO set deployment/cluster/service state => for removal
      //      deployment.copy(clusters = deployment.clusters.map(cluster =>
      //        bp.clusters.find(_.name == cluster.name) match {
      //          case None => cluster
      //          case Some(bpc) => cluster.copy(services = cluster.services.filter(service => !bpc.services.exists(service.breed.name == _.breed.name)))
      //        }
      //      ).filter(_.services.nonEmpty))
      val sliced = deployment
      commit(sliced)
  }

  private def commit(deployment: Deployment): Any = {
    validate(deployment)
    persist(deployment) match {
      case persisted: Deployment =>
        actorFor(DeploymentSynchronizationActor) ! Synchronize(persisted)
        persisted
      case any => any
    }
  }

  private def validate(deployment: Deployment) = {
    val breeds = deployment.clusters.flatMap(_.services).map(_.breed)

    breeds.groupBy(_.name.toString).collect {
      case (name, list) if list.size > 1 => error(NonUniqueBreedReferenceError(list.head))
    }

    val breedNames = breeds.map(_.name.toString).toSet
    breeds.foreach {
      breed => breed.dependencies.values.find(dependency => !breedNames.contains(dependency.name)).flatMap {
        dependency => error(UnresolvedDependencyError(breed, dependency))
      }
    }
  }

  private def persist(deployment: Deployment): Any = {
    implicit val timeout: Timeout = PersistenceActor.timeout
    offLoad(actorFor(PersistenceActor) ? PersistenceActor.Update(deployment, create = true))
  }
}

