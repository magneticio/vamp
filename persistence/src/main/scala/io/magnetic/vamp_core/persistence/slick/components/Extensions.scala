package io.magnetic.vamp_core.persistence.slick.components

import io.magnetic.vamp_core.persistence.slick.extension.VampActiveSlick
import io.magnetic.vamp_core.persistence.slick.model._

import scala.slick.jdbc.JdbcBackend

/**
 * Extensions of the models
 */

trait BlueprintReferenceExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class BlueprintReferenceExtensions(val model: BlueprintReferenceModel) extends ActiveRecord[BlueprintReferenceModel] {
    override def table = BlueprintReferences
  }

}

trait ClusterExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class CLusterExtensions(val model: ClusterModel) extends ActiveRecord[ClusterModel] {
    override def table = Clusters

    // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def services(implicit session: JdbcBackend#Session): List[ServiceModel] =
      for {r <- Services.fetchAll if r.clusterId == model.id.get  && r.deploymentId == model.deploymentId} yield r
  }

}

trait DefaultBlueprintExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class DefaultBlueprintExtensions(val model: DefaultBlueprintModel) extends ActiveRecord[DefaultBlueprintModel] {
    override def table = DefaultBlueprints

    // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def parameters(implicit session: JdbcBackend#Session): List[TraitNameParameterModel] =
      for {r <- TraitNameParameters.fetchAll if r.parentId == model.id.get  && r.deploymentId == model.deploymentId} yield r

    // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def clusters(implicit session: JdbcBackend#Session): List[ClusterModel] =
      for {r <- Clusters.fetchAll if r.blueprintId == model.id.get  && r.deploymentId == model.deploymentId} yield r

    //TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def endpoints(implicit session: JdbcBackend#Session): List[PortModel] =
      for {r <- Ports.fetchAll if r.parentId == model.id && r.parentType == Some(PortParentType.BlueprintEndpoint)  && r.deploymentId == model.deploymentId} yield r

  }

}

trait DefaultEscalationExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class DefaultEscalationExtensions(val model: DefaultEscalationModel) extends ActiveRecord[DefaultEscalationModel] {
    override def table = DefaultEscalations

    def parameters(implicit session: JdbcBackend#Session): List[ParameterModel] =
    // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
      for {r <- Parameters.fetchAll if r.parentId == model.id.get && r.parentType == ParameterParentType.Escalation  && r.deploymentId == model.deploymentId} yield r
  }

}

trait DefaultFilterExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class DefaultFilterExtensions(val model: DefaultFilterModel) extends ActiveRecord[DefaultFilterModel] {
    override def table = DefaultFilters
  }

}

trait DefaultRoutingExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class DefaultRoutingExtensions(val model: DefaultRoutingModel) extends ActiveRecord[DefaultRoutingModel] {
    override def table = DefaultRoutings

    // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def filterReferences(implicit session: JdbcBackend#Session): List[FilterReferenceModel] =
      for {r <- FilterReferences.fetchAll if r.routingId == model.id.get  && r.deploymentId == model.deploymentId} yield r
  }

}

trait DefaultScaleExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class DefaultScaleExtensions(val model: DefaultScaleModel) extends ActiveRecord[DefaultScaleModel] {
    override def table = DefaultScales
  }

}

trait DefaultSlaExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class DefaultSlaExtensions(val model: DefaultSlaModel) extends ActiveRecord[DefaultSlaModel] {
    override def table = DefaultSlas

    // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def parameters(implicit session: JdbcBackend#Session): List[ParameterModel] =
      for {r <- Parameters.fetchAll if r.parentId == model.id.get && r.parentType == ParameterParentType.Sla  && r.deploymentId == model.deploymentId} yield r

    // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def escalationReferences(implicit session: JdbcBackend#Session): List[EscalationReferenceModel] =
      for {r <- EscalationReferences.fetchAll if r.slaId.get == model.id.get  && r.deploymentId == model.deploymentId} yield r

  }

}

trait EscalationReferenceExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class EscalationReferenceExtensions(val model: EscalationReferenceModel) extends ActiveRecord[EscalationReferenceModel] {
    override def table = EscalationReferences
  }

}

trait FilterReferenceExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class FilterReferenceExtensions(val model: FilterReferenceModel) extends ActiveRecord[FilterReferenceModel] {
    override def table = FilterReferences
  }

}

trait RoutingReferenceExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class RoutingReferenceExtensions(val model: RoutingReferenceModel) extends ActiveRecord[RoutingReferenceModel] {
    override def table = RoutingReferences
  }

}

trait ScaleReferenceExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class ScaleReferenceExtensions(val model: ScaleReferenceModel) extends ActiveRecord[ScaleReferenceModel] {
    override def table = ScaleReferences
  }

}

trait ServiceExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class ServiceExtensions(val model: ServiceModel) extends ActiveRecord[ServiceModel] {
    override def table = Services

  }

}

trait SlaReferenceExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class SlaReferenceExtensions(val model: SlaReferenceModel) extends ActiveRecord[SlaReferenceModel] {
    override def table = SlaReferences

    // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def escalationReferences(implicit session: JdbcBackend#Session): List[EscalationReferenceModel] =
      for {r <- EscalationReferences.fetchAll if r.slaRefId.get == model.id.get  && r.deploymentId == model.deploymentId } yield r
  }

}

trait DefaultBreedExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class DefaultBreedExtensions(val model: DefaultBreedModel) extends ActiveRecord[DefaultBreedModel] {
    override def table = DefaultBreeds

    // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def environmentVariables(implicit session: JdbcBackend#Session): List[EnvironmentVariableModel] =
      for {r <- EnvironmentVariables.fetchAll if r.parentId == model.id && r.parentType == Some(EnvironmentVariableParentType.Breed)  && r.deploymentId == model.deploymentId } yield r

    //TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def ports(implicit session: JdbcBackend#Session): List[PortModel] =
      for {r <- Ports.fetchAll if r.parentId == model.id && r.parentType == Some(PortParentType.Breed)  && r.deploymentId == model.deploymentId } yield r

    //TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def dependencies(implicit session: JdbcBackend#Session): List[DependencyModel] =
      for {r <- Dependencies.fetchAll if r.parentId == model.id.get && r.deploymentId == model.deploymentId } yield r

  }

}

