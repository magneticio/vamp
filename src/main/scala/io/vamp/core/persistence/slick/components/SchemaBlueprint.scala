package io.vamp.core.persistence.slick.components

import io.vamp.core.model.artifact.Trait
import io.strongtyped.active.slick.Profile
import io.vamp.core.persistence.slick.extension.{VampTableQueries, VampTables}
import io.vamp.core.persistence.slick.model.TraitParameterParentType.TraitParameterParentType
import io.vamp.core.persistence.slick.model._

import scala.language.implicitConversions

trait SchemaBlueprint extends SchemaBreed {
  this: VampTables with VampTableQueries with Profile =>

  import Implicits._
  import jdbcDriver.simple._

  val DefaultBlueprints = AnonymousNameableEntityTableQuery[DefaultBlueprintModel, DefaultBlueprintTable](tag => new DefaultBlueprintTable(tag))
  val BlueprintReferences = DeployableNameEntityTableQuery[BlueprintReferenceModel, BlueprintReferenceTable](tag => new BlueprintReferenceTable(tag))
  val Clusters = DeployableNameEntityTableQuery[ClusterModel, ClusterTable](tag => new ClusterTable(tag))
  val Services = EntityTableQuery[ServiceModel, ServiceTable](tag => new ServiceTable(tag))
  val TraitNameParameters = NameableEntityTableQuery[TraitNameParameterModel, TraitNameParameterTable](tag => new TraitNameParameterTable(tag))

  class DefaultBlueprintTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultBlueprintModel](tag, "default_blueprints") {
    def * = (deploymentId, name, id.?, isAnonymous) <>(DefaultBlueprintModel.tupled, DefaultBlueprintModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def isAnonymous = column[Boolean]("anonymous")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def name = column[String]("name")

    def idx = index("idx_default_blueprint", (name, deploymentId), unique = true)
  }

  class BlueprintReferenceTable(tag: Tag) extends DeployableEntityTable[BlueprintReferenceModel](tag, "blueprint_references") {
    def * = (deploymentId, name, id.?, isDefinedInline) <>(BlueprintReferenceModel.tupled, BlueprintReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def idx = index("idx_blueprint_reference", (name, deploymentId), unique = true)
  }

  class ClusterTable(tag: Tag) extends DeployableEntityTable[ClusterModel](tag, "clusters") {
    def * = (deploymentId, name, blueprintId, slaReferenceId, id.?) <>(ClusterModel.tupled, ClusterModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def slaReferenceId = column[Option[Int]]("sla_reference_id")

    def idx = index("idx_cluster", (name, blueprintId, deploymentId), unique = true)

    def blueprintId = column[Int]("blueprint_id")

    def name = column[String]("name")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def blueprint = foreignKey("cluster_blueprintfk", blueprintId, DefaultBlueprints)(_.id)

    def slaRef = foreignKey("cluster_sla_reference_fk", slaReferenceId, SlaReferences)(_.id)
  }

  class ServiceTable(tag: Tag) extends EntityTable[ServiceModel](tag, "services") {
    def * = (deploymentId, clusterId, breedReferenceId, routingReference, scaleReference, id.?) <>(ServiceModel.tupled, ServiceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def clusterId = column[Int]("clusterid")

    def breedReferenceId = column[Int]("breed_reference_id")

    def routingReference = column[Option[Int]]("routing_reference_fk")

    def scaleReference = column[Option[Int]]("scale_reference_fk")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def scale = foreignKey("service_scale_reference_fk", scaleReference, ScaleReferences)(_.id)

    def routing = foreignKey("service_routing_reference_fk", routingReference, RoutingReferences)(_.id)

    def cluster = foreignKey("service_cluster_fk", clusterId, Clusters)(_.id)

    def breedReference = foreignKey("service_breed_reference_fk", breedReferenceId, BreedReferences)(_.id)
  }

  class TraitNameParameterTable(tag: Tag) extends NameableEntityTable[TraitNameParameterModel](tag, "trait_name_parameters") {
    def * = (id.?, name, scope, groupType, stringValue, groupId, parentId, parentType) <>(TraitNameParameterModel.tupled, TraitNameParameterModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def stringValue = column[Option[String]]("string_value")

    def groupId = column[Option[Int]]("group_id")

    def idx = index("idx_trait_name_parameters", (name, scope, groupType, parentId, parentType), unique = true)

    def name = column[String]("name")

    def scope = column[Option[String]]("param_scope")

    def groupType = column[Option[Trait.Name.Group.Value]]("param_group")

    def parentId = column[Option[Int]]("parent_id")

    def parentType = column[TraitParameterParentType]("parent_type")
  }

}
