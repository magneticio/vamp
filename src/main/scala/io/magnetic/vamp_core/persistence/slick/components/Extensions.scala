package io.magnetic.vamp_core.persistence.slick.components

import io.magnetic.vamp_core.persistence.slick.extension.VampActiveSlick
import io.magnetic.vamp_core.persistence.slick.model._


import scala.slick.jdbc.JdbcBackend

/**
 * Extensions of the models
 */

trait DeploymentExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class DeploymentExtensions(val model: DeploymentModel) extends ActiveRecord[DeploymentModel] {
    override def table = Deployments

    // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def parameters(implicit session: JdbcBackend#Session): List[TraitNameParameterModel] =
      for {r <- TraitNameParameters.fetchAllFromDeployment(model.id) if r.deploymentId == model.id} yield r

    // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def clusters(implicit session: JdbcBackend#Session): List[DeploymentClusterModel] =
      for {r <- DeploymentClusters.fetchAllFromDeployment(model.id) if r.deploymentId == model.id  } yield r

    //TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def endpoints(implicit session: JdbcBackend#Session): List[PortModel] =
      for {r <- Ports.fetchAllFromDeployment(model.id) if r.deploymentId == model.id && r.parentId == model.id && r.parentType == Some(PortParentType.BlueprintEndpoint) } yield r

  }

}
trait DeploymentClusterExtensions {
  this: VampActiveSlick with ModelExtensions =>

implicit class DeploymentClusterExtensions(val model: DeploymentClusterModel) extends ActiveRecord[DeploymentClusterModel] {
  override def table = DeploymentClusters

  // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
  def services(implicit session: JdbcBackend#Session): List[DeploymentServiceModel] =
    for {r <- DeploymentServices.fetchAllFromDeployment(model.id) if r.clusterId == model.id.get} yield r

  def routes(implicit session: JdbcBackend#Session): List[ClusterRouteModel] =
    for {r <- ClusterRoutes.fetchAll if r.clusterId == model.id.get} yield r

}

}


trait DeploymentServiceExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class DeploymentServiceExtensions(val model: DeploymentServiceModel) extends ActiveRecord[DeploymentServiceModel] {
    override def table = DeploymentServices

    // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def servers(implicit session: JdbcBackend#Session): List[DeploymentServerModel] =
      for {r <- DeploymentServers.fetchAllFromDeployment(model.id) if r.serviceId == model.id.get} yield r

    def dependencies(implicit session: JdbcBackend#Session): List[DeploymentServiceDependencyModel] =
      for {r <- DeploymentServiceDependencies.fetchAll if r.serviceId == model.id.get} yield r


  }

}

trait DeploymentServerExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class DeploymentServerExtensions(val model: DeploymentServerModel) extends ActiveRecord[DeploymentServerModel] {
    override def table = DeploymentServers

    // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def ports(implicit session: JdbcBackend#Session): List[ServerPortModel] =
       for {r <- ServerPorts.fetchAll if r.serverId == model.id.get} yield r

  }

}


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
      for {r <- TraitNameParameters.fetchAllFromDeployment(model.deploymentId) if r.parentId == model.id.get } yield r

    // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def clusters(implicit session: JdbcBackend#Session): List[ClusterModel] =
      for {r <- Clusters.fetchAllFromDeployment(model.deploymentId) if r.blueprintId == model.id.get } yield r

    //TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def endpoints(implicit session: JdbcBackend#Session): List[PortModel] =
      for {r <- Ports.fetchAllFromDeployment(model.deploymentId) if r.parentId == model.id && r.parentType == Some(PortParentType.BlueprintEndpoint) } yield r

  }

}

trait DefaultEscalationExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class DefaultEscalationExtensions(val model: DefaultEscalationModel) extends ActiveRecord[DefaultEscalationModel] {
    override def table = DefaultEscalations

    def parameters(implicit session: JdbcBackend#Session): List[ParameterModel] =
    // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
      for {r <- Parameters.fetchAllFromDeployment(model.deploymentId) if r.parentId == model.id.get && r.parentType == ParameterParentType.Escalation} yield r
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
      for {r <- FilterReferences.fetchAllFromDeployment(model.deploymentId) if r.routingId == model.id.get  } yield r
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
      for {r <- Parameters.fetchAllFromDeployment(model.deploymentId) if r.parentId == model.id.get && r.parentType == ParameterParentType.Sla} yield r

    // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def escalationReferences(implicit session: JdbcBackend#Session): List[EscalationReferenceModel] =
      for {r <- EscalationReferences.fetchAllFromDeployment(model.deploymentId) if r.slaId.get == model.id.get  } yield r

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
      for {r <- EscalationReferences.fetchAllFromDeployment(model.deploymentId) if r.slaRefId.get == model.id.get  } yield r
  }

}

trait DefaultBreedExtensions {
  this: VampActiveSlick with ModelExtensions =>

  implicit class DefaultBreedExtensions(val model: DefaultBreedModel) extends ActiveRecord[DefaultBreedModel] {
    override def table = DefaultBreeds

    // TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def environmentVariables(implicit session: JdbcBackend#Session): List[EnvironmentVariableModel] =
      for {r <- EnvironmentVariables.fetchAllFromDeployment(model.deploymentId) if r.parentId == model.id && r.parentType == Some(EnvironmentVariableParentType.Breed) } yield r

    //TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def ports(implicit session: JdbcBackend#Session): List[PortModel] =
      for {r <- Ports.fetchAllFromDeployment(model.deploymentId) if r.parentId == model.id && r.parentType == Some(PortParentType.Breed)  } yield r

    //TODO FIX: This filters the data in Scala, not in the DB (bad!!)
    def dependencies(implicit session: JdbcBackend#Session): List[DependencyModel] =
      for {r <- Dependencies.fetchAllFromDeployment(model.deploymentId) if r.parentId == model.id.get } yield r

  }

}

