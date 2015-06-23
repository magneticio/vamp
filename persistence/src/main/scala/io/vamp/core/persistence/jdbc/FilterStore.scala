package io.vamp.core.persistence.jdbc

import io.vamp.core.model.artifact.{Artifact, DefaultFilter, Filter, FilterReference}
import io.vamp.core.persistence.notification.PersistenceNotificationProvider
import io.vamp.core.persistence.slick.model.{DefaultRoutingModel, DeploymentDefaultFilter, DeploymentDefaultRouting, FilterReferenceModel}

import scala.slick.jdbc.JdbcBackend

trait FilterStore extends PersistenceNotificationProvider {

  implicit val sess: JdbcBackend.Session

  import io.vamp.core.persistence.slick.components.Components.instance._
  import io.vamp.core.persistence.slick.model.Implicits._

  protected def createFilterReferences(a: DeploymentDefaultRouting, routingId: DefaultRoutingModel#Id): Unit = {
    for (filter <- a.artifact.filters) {
      filter match {
        case f: DefaultFilter =>
          val filterId: Int = DefaultFilters.findOptionByName(f.name, a.deploymentId) match {
            case Some(existing) =>
              DefaultFilters.update(existing.copy(condition = f.condition))
              existing.id.get
            case _ =>
              DefaultFilters.add(DeploymentDefaultFilter(a.deploymentId, f))
          }
          val defFilter = DefaultFilters.findById(filterId)
          FilterReferences.add(FilterReferenceModel(deploymentId = a.deploymentId, name = defFilter.name, routingId = routingId, isDefinedInline = true))

        case f: FilterReference =>
          FilterReferences.add(FilterReferenceModel(deploymentId = a.deploymentId, name = filter.name, routingId = routingId, isDefinedInline = false))
      }
    }
  }

  protected def deleteFilterReferences(filterReferences: List[FilterReferenceModel]): Unit = {
    for (filter <- filterReferences) {
      if (filter.isDefinedInline) {
        DefaultFilters.findOptionByName(filter.name, filter.deploymentId) match {
          case Some(storedFilter) if storedFilter.isAnonymous => DefaultFilters.deleteByName(filter.name, filter.deploymentId)
          case _ =>
        }
      }
      FilterReferences.deleteByName(filter.name, filter.deploymentId)
    }
  }

  protected def findFilterOptionArtifact(name: String, defaultDeploymentId: Option[Int] = None): Option[Artifact] = {
    DefaultFilters.findOptionByName(name, defaultDeploymentId).map(a => a)
  }

  protected def deleteFilterFromDb(artifact: DefaultFilter): Unit = {
    DefaultFilters.deleteByName(artifact.name, None)
  }

  protected def createFilterArtifact(art: Filter): String = art match {
    case a: DefaultFilter => DefaultFilters.findById(DefaultFilters.add(DeploymentDefaultFilter(None, a))).name

  }


}
