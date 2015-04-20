package io.vamp.core.persistence.store.jdbc

import io.vamp.core.model.artifact._
import io.vamp.core.persistence.notification.{ArtifactNotFound, PersistenceNotificationProvider}
import io.vamp.core.persistence.slick.model.{DefaultFilterModel, DefaultRoutingModel, DeploymentDefaultRouting, RoutingReferenceModel}
import io.vamp.core.persistence.slick.util.VampPersistenceUtil

import scala.slick.jdbc.JdbcBackend

trait RoutingStore extends FilterStore with PersistenceNotificationProvider {

  implicit val sess: JdbcBackend.Session

  import io.vamp.core.persistence.slick.components.Components.instance._
  import io.vamp.core.persistence.slick.model.Implicits._

  protected def findOptionRoutingArtifactViaReference(referenceId: Option[Int], deploymentId: Option[Int]): Option[Routing] = referenceId match {
    case Some(routingRefId) =>
      RoutingReferences.findOptionById(routingRefId) match {
        case Some(ref: RoutingReferenceModel) if ref.isDefinedInline =>
          DefaultRoutings.findOptionByName(ref.name, deploymentId) match {
            case Some(defaultRouting) => Some(defaultRoutingModel2Artifact(defaultRouting))
            case None => Some(RoutingReference(name = ref.name)) // Not found, return a reference instead
          }
        case Some(ref) => Some(RoutingReference(name = ref.name))
        case None => None
      }
    case None => None
  }

  protected def createRoutingReference(artifact: Option[Routing], deploymentId: Option[Int]): Option[Int] = artifact match {
    case Some(routing: DefaultRouting) =>
      DefaultRoutings.findOptionByName(routing.name, deploymentId) match {
        case Some(existing) => updateRouting(existing, routing)
          Some(RoutingReferences.add(RoutingReferenceModel(deploymentId = deploymentId, name = existing.name, isDefinedInline = true)))
        case None =>
          val routingName = createDefaultRoutingModelFromArtifact(DeploymentDefaultRouting(deploymentId, routing)).name
          Some(RoutingReferences.add(RoutingReferenceModel(deploymentId = deploymentId, name = routingName, isDefinedInline = true)))
      }
    case Some(routing: RoutingReference) =>
      Some(RoutingReferences.add(RoutingReferenceModel(deploymentId = deploymentId, name = routing.name, isDefinedInline = false)))
    case _ => None
  }

  protected def createDefaultRoutingModelFromArtifact(artifact: DeploymentDefaultRouting): DefaultRoutingModel = {
    val routingId = DefaultRoutings.add(artifact)
    createFilterReferences(artifact, routingId)
    DefaultRoutings.findById(routingId)
  }

  protected def updateRouting(existing: DefaultRoutingModel, artifact: DefaultRouting): Unit = {
    deleteFilterReferences(existing.filterReferences)
    createFilterReferences(DeploymentDefaultRouting(existing.deploymentId, artifact), existing.id.get)
    existing.copy(weight = artifact.weight).update
  }

  protected def findRoutingOptionArtifact(name: String, defaultDeploymentId: Option[Int] = None): Option[Artifact] = {
    DefaultRoutings.findOptionByName(name, defaultDeploymentId) match {
      case Some(r) => Some(defaultRoutingModel2Artifact(r))
      case None => None
    }
  }

  protected def defaultRoutingModel2Artifact(r: DefaultRoutingModel): DefaultRouting = {
    val filters: List[Filter] = r.filterReferences.map(filter =>
      if (filter.isDefinedInline)
        DefaultFilters.findOptionByName(filter.name, filter.deploymentId) match {
          case Some(defaultFilter: DefaultFilterModel) => defaultFilterModel2Artifact(defaultFilter)
          case _ => FilterReference(filter.name)
        }
      else
        FilterReference(filter.name)
    )
    DefaultRouting(name = VampPersistenceUtil.restoreToAnonymous(r.name, r.isAnonymous), weight = r.weight, filters = filters)
  }

  protected def deleteRoutingFromDb(artifact: Routing): Unit = {
    DefaultRoutings.findOptionByName(artifact.name, None) match {
      case Some(routing) =>
        deleteRoutingModel(routing)
      case None => throw exception(ArtifactNotFound(artifact.name, artifact.getClass))
    }
  }

  protected def deleteRoutingModel(routing: DefaultRoutingModel): Unit = {
    deleteFilterReferences(routing.filterReferences)
    DefaultRoutings.deleteById(routing.id.get)
  }

  protected def createRoutingArtifact(art: Routing): String = art match {
    case a: DefaultRouting => createDefaultRoutingModelFromArtifact(DeploymentDefaultRouting(None, a)).name
  }

}
