package io.vamp.core.persistence.slick.components

import io.strongtyped.active.slick.Profile
import io.vamp.core.model.artifact.Trait
import io.vamp.core.persistence.slick.extension.{VampTableQueries, VampTables}
import io.vamp.core.persistence.slick.model.EnvironmentVariableParentType.EnvironmentVariableParentType
import io.vamp.core.persistence.slick.model.ParameterParentType.ParameterParentType
import io.vamp.core.persistence.slick.model.ParameterType.ParameterType
import io.vamp.core.persistence.slick.model.PortParentType.PortParentType
import io.vamp.core.persistence.slick.model.PortType.PortType
import io.vamp.core.persistence.slick.model._

import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions
import scala.slick.util.Logging

trait SchemaBreed extends Logging with VampSchema {
  this: VampTables with VampTableQueries with Profile =>

  import Implicits._
  import jdbcDriver.simple._

  val SlaReferences = DeployableNameEntityTableQuery[SlaReferenceModel, SlaReferenceTable](tag => new SlaReferenceTable(tag))
  val GenericSlas = AnonymousNameableEntityTableQuery[GenericSlaModel, GenericSlaTable](tag => new GenericSlaTable(tag))
  val EscalationReferences = DeployableNameEntityTableQuery[EscalationReferenceModel, EscalationReferenceTable](tag => new EscalationReferenceTable(tag))
  val GenericEscalations = AnonymousNameableEntityTableQuery[GenericEscalationModel, GenericEscalationTable](tag => new GenericEscalationTable(tag))
  val ScaleReferences = DeployableNameEntityTableQuery[ScaleReferenceModel, ScaleReferenceTable](tag => new ScaleReferenceTable(tag))
  val DefaultScales = AnonymousNameableEntityTableQuery[DefaultScaleModel, DefaultScaleTable](tag => new DefaultScaleTable(tag))
  val RoutingReferences = DeployableNameEntityTableQuery[RoutingReferenceModel, RoutingReferenceTable](tag => new RoutingReferenceTable(tag))
  val DefaultRoutings = AnonymousNameableEntityTableQuery[DefaultRoutingModel, DefaultRoutingTable](tag => new DefaultRoutingTable(tag))
  val FilterReferences = DeployableNameEntityTableQuery[FilterReferenceModel, FilterReferenceTable](tag => new FilterReferenceTable(tag))
  val DefaultFilters = AnonymousNameableEntityTableQuery[DefaultFilterModel, DefaultFilterTable](tag => new DefaultFilterTable(tag))
  val BreedReferences = DeployableNameEntityTableQuery[BreedReferenceModel, BreedReferenceTable](tag => new BreedReferenceTable(tag))
  val DefaultBreeds = AnonymousNameableEntityTableQuery[DefaultBreedModel, DefaultBreedTable](tag => new DefaultBreedTable(tag))
  val EnvironmentVariables = DeployableNameEntityTableQuery[EnvironmentVariableModel, EnvironmentVariableTable](tag => new EnvironmentVariableTable(tag))
  val Ports = NameableEntityTableQuery[PortModel, PortTable](tag => new PortTable(tag))
  val Dependencies = DeployableNameEntityTableQuery[DependencyModel, DependencyTable](tag => new DependencyTable(tag))
  val Parameters = DeployableNameEntityTableQuery[ParameterModel, ParameterTable](tag => new ParameterTable(tag))


  class SlaReferenceTable(tag: Tag) extends DeployableEntityTable[SlaReferenceModel](tag, "sla_references") {
    def * = (deploymentId, name, id.?, isDefinedInline) <>(SlaReferenceModel.tupled, SlaReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def deployment = foreignKey("sla_reference_deployment_fk", deploymentId, Deployments)(_.id)
  }

  class GenericSlaTable(tag: Tag) extends AnonymousNameableEntityTable[GenericSlaModel](tag, "generic_slas") {
    def * = (deploymentId, name, slaType, id.?, upper, lower, interval, cooldown, isAnonymous) <>(GenericSlaModel.tupled, GenericSlaModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def slaType = column[String]("sla_type")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def isAnonymous = column[Boolean]("anonymous")

    def name = column[String]("name")

    def upper = column[Option[FiniteDuration]]("upper")

    def lower = column[Option[FiniteDuration]]("lower")

    def interval = column[Option[FiniteDuration]]("interval")

    def cooldown = column[Option[FiniteDuration]]("cooldown")

    def idx = index("idx_generic_sla", (name, deploymentId), unique = true)

    def deployment = foreignKey("generic_sla_deployment_fk", deploymentId, Deployments)(_.id)
  }

  class EscalationReferenceTable(tag: Tag) extends DeployableEntityTable[EscalationReferenceModel](tag, "escalation_references") {
    def * = (deploymentId, name, slaId, slaRefId, parentEscalationId, id.?, isDefinedInline) <>(EscalationReferenceModel.tupled, EscalationReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def slaId = column[Option[Int]]("sla_id")

    def slaRefId = column[Option[Int]]("sla_ref_id")

    def parentEscalationId = column[Option[Int]]("escalation_parent_id")

    def name = column[String]("name")

    def idx = index("idx_escalation_references", (name, slaId, slaRefId, deploymentId), unique = true)

    def sla = foreignKey("escalation_sla_fk", slaId, GenericSlas)(_.id)

    def slaRef = foreignKey("escalation_sla_reference_fk", slaRefId, SlaReferences)(_.id)

    def escalationParent = foreignKey("escalation_escalation_parent_fk", parentEscalationId, GenericEscalations)(_.id)

    def deployment = foreignKey("escalation_reference_deployment_fk", deploymentId, Deployments)(_.id)
  }

  class GenericEscalationTable(tag: Tag) extends AnonymousNameableEntityTable[GenericEscalationModel](tag, "generic_escalations") {
    def * = (deploymentId, name, escalationType, minimumInt, maximumInt, scaleByInt, minimumDouble, maximumDouble, scaleByDouble, targetCluster, id.?, isAnonymous) <>(GenericEscalationModel.tupled, GenericEscalationModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def escalationType = column[String]("escalation_type")

    def isAnonymous = column[Boolean]("anonymous")

    def idx = index("idx_generic_escalation", (name, deploymentId), unique = true)

    def deploymentId = column[Option[Int]]("deployment_fk")

    def name = column[String]("name")

    def minimumInt = column[Option[Int]]("minimum_int")

    def maximumInt = column[Option[Int]]("maximum_int")

    def scaleByInt = column[Option[Int]]("scale_by_int")

    def minimumDouble = column[Option[Double]]("minimum_double")

    def maximumDouble = column[Option[Double]]("maximum_double")

    def scaleByDouble = column[Option[Double]]("scale_by_double")

    def targetCluster = column[Option[String]]("target_cluster")

    def deployment = foreignKey("generic_escalation_deployment_fk", deploymentId, Deployments)(_.id)
  }

  class ScaleReferenceTable(tag: Tag) extends DeployableEntityTable[ScaleReferenceModel](tag, "scale_references") {
    def * = (deploymentId, name, id.?, isDefinedInline) <>(ScaleReferenceModel.tupled, ScaleReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def deployment = foreignKey("scale_reference_deployment_fk", deploymentId, Deployments)(_.id)
  }

  class DefaultScaleTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultScaleModel](tag, "default_scales") {
    def * = (deploymentId, name, cpu, memory, instances, id.?, isAnonymous) <>(DefaultScaleModel.tupled, DefaultScaleModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def cpu = column[Double]("cpu")

    def memory = column[Double]("memory")

    def instances = column[Int]("instances")

    def isAnonymous = column[Boolean]("anonymous")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def name = column[String]("name")

    def idx = index("idx_default_scale", (name, deploymentId), unique = true)

    def deployment = foreignKey("default_scale_deployment_fk", deploymentId, Deployments)(_.id)
  }

  class RoutingReferenceTable(tag: Tag) extends DeployableEntityTable[RoutingReferenceModel](tag, "routing_references") {
    def * = (deploymentId, name, id.?, isDefinedInline) <>(RoutingReferenceModel.tupled, RoutingReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def name = column[String]("name")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def deployment = foreignKey("routing_reference_deployment_fk", deploymentId, Deployments)(_.id)
  }

  class DefaultRoutingTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultRoutingModel](tag, "default_routings") {
    def * = (deploymentId, name, weight, id.?, isAnonymous) <>(DefaultRoutingModel.tupled, DefaultRoutingModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def weight = column[Option[Int]]("weight")

    def isAnonymous = column[Boolean]("anonymous")

    def idx = index("idx_default_routing", (name, deploymentId), unique = true)

    def deploymentId = column[Option[Int]]("deployment_fk")

    def name = column[String]("name")

    def deployment = foreignKey("default_routing_deployment_fk", deploymentId, Deployments)(_.id)
  }

  class FilterReferenceTable(tag: Tag) extends DeployableEntityTable[FilterReferenceModel](tag, "filter_references") {
    def * = (deploymentId, name, id.?, routingId, isDefinedInline) <>(FilterReferenceModel.tupled, FilterReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def name = column[String]("name")

    def routingId = column[Int]("routing_id")

    def idx = index("idx_filter_reference", (name, routingId, deploymentId), unique = true)

    def routing = foreignKey("filter_ref_routing_fk", routingId, DefaultRoutings)(_.id)

    def deployment = foreignKey("filter_reference_eployment_fk", deploymentId, Deployments)(_.id)
  }

  class DefaultFilterTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultFilterModel](tag, "default_filters") {
    def * = (deploymentId, name, condition, id.?, isAnonymous) <>(DefaultFilterModel.tupled, DefaultFilterModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def condition = column[String]("condition")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def isAnonymous = column[Boolean]("anonymous")

    def idx = index("idx_default_filter", name, unique = true)

    def deployment = foreignKey("default_filter_deployment_fk", deploymentId, Deployments)(_.id)
  }

  class BreedReferenceTable(tag: Tag) extends DeployableEntityTable[BreedReferenceModel](tag, "breed_references") {
    def * = (deploymentId, name, id.?, isDefinedInline) <>(BreedReferenceModel.tupled, BreedReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def deployment = foreignKey("breed_reference_deployment_fk", deploymentId, Deployments)(_.id)
  }

  class DefaultBreedTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultBreedModel](tag, "default_breeds") {
    def * = (deploymentId, name, deployable, id.?, isAnonymous) <>(DefaultBreedModel.tupled, DefaultBreedModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def deployable = column[String]("deployable")

    def isAnonymous = column[Boolean]("anonymous")

    def idx = index("idx_default_breed", (name, deploymentId), unique = true)

    def deploymentId = column[Option[Int]]("deployment_fk")

    def name = column[String]("name")

    def deployment = foreignKey("default_breed_deployment_fk", deploymentId, Deployments)(_.id)
  }

  class EnvironmentVariableTable(tag: Tag) extends DeployableEntityTable[EnvironmentVariableModel](tag, "environment_variables") {
    def * = (deploymentId, name, scope, groupType, alias, value, direction, id.?, parentId, parentType) <>(EnvironmentVariableModel.tupled, EnvironmentVariableModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def alias = column[Option[String]]("alias")

    def value = column[Option[String]]("env_value")

    def direction = column[Trait.Direction.Value]("env_direction")

    def name = column[String]("name")

    def scope = column[Option[String]]("trait_scope")

    def groupType = column[Option[Trait.Name.Group.Value]]("trait_group")

    def parentId = column[Option[Int]]("parent_id")

    def parentType = column[Option[EnvironmentVariableParentType]]("parent_type")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def idx = index("idx_environment_variables", (name, groupType, scope, parentId, parentType), unique = true)

    def deployment = foreignKey("environment_variables_deployment_fk", deploymentId, Deployments)(_.id)
  }

  class PortTable(tag: Tag) extends NameableEntityTable[PortModel](tag, "ports") {
    def * = (name, scope, groupType, alias, portType, value, direction, id.?, parentId, parentType) <>(PortModel.tupled, PortModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def alias = column[Option[String]]("alias")

    def portType = column[PortType]("port_type")

    def value = column[Option[Int]]("port_value")

    def direction = column[Trait.Direction.Value]("port_direction")

    def idx = index("idx_ports", (name, parentId, parentType), unique = true)

    def name = column[String]("name")

    def scope = column[Option[String]]("trait_scope")

    def groupType = column[Option[Trait.Name.Group.Value]]("trait_group")

    def parentId = column[Option[Int]]("parent_id")

    def parentType = column[Option[PortParentType]]("parent_type")
  }

  class DependencyTable(tag: Tag) extends DeployableEntityTable[DependencyModel](tag, "breed_dependencies") {
    def * = (deploymentId, name, breedName, id.?, isDefinedInline, parentId) <>(DependencyModel.tupled, DependencyModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def name = column[String]("name")

    def breedName = column[String]("breed_name")

    def parentId = column[Int]("parent_id")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def idx = index("idx_breed_dependencies", (name, breedName, parentId, deploymentId), unique = true)

    def deployment = foreignKey("dependency_table_deployment_fk", deploymentId, Deployments)(_.id)
  }

  class ParameterTable(tag: Tag) extends DeployableEntityTable[ParameterModel](tag, "parameters") {
    def * = (deploymentId, name, stringValue, intValue, doubleValue, parameterType, id.?, parentType, parentId) <>(ParameterModel.tupled, ParameterModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def stringValue = column[Option[String]]("string_value")

    def intValue = column[Int]("int_value")

    def doubleValue = column[Double]("double_value")

    def parameterType = column[ParameterType]("parameter_type")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def idx = index("idx_parameters", (name, parentType, parentId), unique = true)

    def name = column[String]("name")

    def parentType = column[ParameterParentType]("parent_type")

    def parentId = column[Int]("parent_id")

    def deployment = foreignKey("parameter_deployment_fk", deploymentId, Deployments)(_.id)
  }


}
