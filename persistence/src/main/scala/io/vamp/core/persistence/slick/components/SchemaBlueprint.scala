package io.vamp.core.persistence.slick.components

import io.strongtyped.active.slick.Profile
import io.vamp.core.persistence.slick.extension.{ VampTableQueries, VampTables }
import io.vamp.core.persistence.slick.model._

import scala.language.implicitConversions

trait SchemaBlueprint extends SchemaBreed {
  this: VampTables with VampTableQueries with Profile ⇒

  import jdbcDriver.simple._

  val DefaultBlueprints = AnonymousNameableEntityTableQuery[DefaultBlueprintModel, DefaultBlueprintTable](tag ⇒ new DefaultBlueprintTable(tag))
  val BlueprintReferences = DeployableNameEntityTableQuery[BlueprintReferenceModel, BlueprintReferenceTable](tag ⇒ new BlueprintReferenceTable(tag))
  val Clusters = DeployableNameEntityTableQuery[ClusterModel, ClusterTable](tag ⇒ new ClusterTable(tag))
  val Services = EntityTableQuery[ServiceModel, ServiceTable](tag ⇒ new ServiceTable(tag))

  class DefaultBlueprintTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultBlueprintModel](tag, "default_blueprints") {
    def * = (deploymentId, name, id.?, isAnonymous) <> (DefaultBlueprintModel.tupled, DefaultBlueprintModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def isAnonymous = column[Boolean]("anonymous")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def name = column[String]("name")

    def idx = index("idx_default_blueprint", (name, deploymentId), unique = true)

    def deployment = foreignKey("default_blueprint_deployment_fk", deploymentId, Deployments)(_.id)
  }

  class BlueprintReferenceTable(tag: Tag) extends DeployableEntityTable[BlueprintReferenceModel](tag, "blueprint_references") {
    def * = (deploymentId, name, id.?, isDefinedInline) <> (BlueprintReferenceModel.tupled, BlueprintReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def idx = index("idx_blueprint_reference", (name, deploymentId), unique = true)

    def deployment = foreignKey("blueprint_reference_deployment_fk", deploymentId, Deployments)(_.id)
  }

  class ClusterTable(tag: Tag) extends DeployableEntityTable[ClusterModel](tag, "clusters") {
    def * = (deploymentId, name, blueprintId, slaReferenceId, dialects, id.?) <> (ClusterModel.tupled, ClusterModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def slaReferenceId = column[Option[Int]]("sla_reference_id")

    def idx = index("idx_cluster", (name, blueprintId, deploymentId), unique = true)

    def blueprintId = column[Int]("blueprint_id")

    def name = column[String]("name")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def dialects = column[Array[Byte]]("dialects")

    def blueprint = foreignKey("cluster_blueprintfk", blueprintId, DefaultBlueprints)(_.id)

    def slaRef = foreignKey("cluster_sla_reference_fk", slaReferenceId, SlaReferences)(_.id)

    def deployment = foreignKey("cluster_deployment_fk", deploymentId, Deployments)(_.id)
  }

  class ServiceTable(tag: Tag) extends EntityTable[ServiceModel](tag, "services") {
    def * = (deploymentId, clusterId, breedReferenceId, routingReference, scaleReference, dialects, id.?) <> (ServiceModel.tupled, ServiceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def clusterId = column[Int]("clusterid")

    def breedReferenceId = column[Int]("breed_reference_id")

    def routingReference = column[Option[Int]]("routing_reference_fk")

    def scaleReference = column[Option[Int]]("scale_reference_fk")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def dialects = column[Array[Byte]]("dialects")

    def scale = foreignKey("service_scale_reference_fk", scaleReference, ScaleReferences)(_.id)

    def routing = foreignKey("service_routing_reference_fk", routingReference, RoutingReferences)(_.id)

    def cluster = foreignKey("service_cluster_fk", clusterId, Clusters)(_.id)

    def breedReference = foreignKey("service_breed_reference_fk", breedReferenceId, BreedReferences)(_.id)

    def deployment = foreignKey("service_deployment_fk", deploymentId, Deployments)(_.id)
  }

}
