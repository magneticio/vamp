package io.magnetic.vamp_core.persistence.slick.components

import io.magnetic.vamp_core.model.artifact.Trait
import io.magnetic.vamp_core.persistence.slick.extension.{VampTableQueries, VampTables}
import io.magnetic.vamp_core.persistence.slick.model.ParameterParentType.ParameterParentType
import io.magnetic.vamp_core.persistence.slick.model.ParameterType.ParameterType
import io.magnetic.vamp_core.persistence.slick.model.PortType.PortType
import io.magnetic.vamp_core.persistence.slick.model._
import io.strongtyped.active.slick.Profile

import scala.language.implicitConversions
import scala.slick.util.Logging

trait Schema extends Logging {
  this: VampTables with VampTableQueries with Profile =>

  import io.magnetic.vamp_core.persistence.slick.model.Implicits._
  import jdbcDriver.simple._

  val DefaultBlueprints = AnonymousNameableEntityTableQuery[DefaultBlueprintModel, DefaultBlueprintTable](tag => new DefaultBlueprintTable(tag))
  val BlueprintReferences = NameableEntityTableQuery[BlueprintReferenceModel, BlueprintReferenceTable](tag => new BlueprintReferenceTable(tag))
  val Clusters = NameableEntityTableQuery[ClusterModel, ClusterTable](tag => new ClusterTable(tag))
  val Services = EntityTableQuery[ServiceModel, ServiceTable](tag => new ServiceTable(tag))
  val SlaReferences = NameableEntityTableQuery[SlaReferenceModel, SlaReferenceTable](tag => new SlaReferenceTable(tag))
  val DefaultSlas = AnonymousNameableEntityTableQuery[DefaultSlaModel, DefaultSlaTable](tag => new DefaultSlaTable(tag))
  val EscalationReferences = NameableEntityTableQuery[EscalationReferenceModel, EscalationReferenceTable](tag => new EscalationReferenceTable(tag))
  val DefaultEscalations = AnonymousNameableEntityTableQuery[DefaultEscalationModel, DefaultEscalationTable](tag => new DefaultEscalationTable(tag))
  val ScaleReferences = NameableEntityTableQuery[ScaleReferenceModel, ScaleReferenceTable](tag => new ScaleReferenceTable(tag))
  val DefaultScales = AnonymousNameableEntityTableQuery[DefaultScaleModel, DefaultScaleTable](tag => new DefaultScaleTable(tag))
  val RoutingReferences = NameableEntityTableQuery[RoutingReferenceModel, RoutingReferenceTable](tag => new RoutingReferenceTable(tag))
  val DefaultRoutings = AnonymousNameableEntityTableQuery[DefaultRoutingModel, DefaultRoutingTable](tag => new DefaultRoutingTable(tag))
  val FilterReferences = NameableEntityTableQuery[FilterReferenceModel, FilterReferenceTable](tag => new FilterReferenceTable(tag))
  val DefaultFilters = AnonymousNameableEntityTableQuery[DefaultFilterModel, DefaultFilterTable](tag => new DefaultFilterTable(tag))
  val BreedReferences = NameableEntityTableQuery[BreedReferenceModel, BreedReferenceTable](tag => new BreedReferenceTable(tag))
  val DefaultBreeds = AnonymousNameableEntityTableQuery[DefaultBreedModel, DefaultBreedTable](tag => new DefaultBreedTable(tag))
  val EnvironmentVariables = NameableEntityTableQuery[EnvironmentVariableModel, EnvironmentVariableTable](tag => new EnvironmentVariableTable(tag))
  val Ports = NameableEntityTableQuery[PortModel, PortTable](tag => new PortTable(tag))
  val Dependencies = NameableEntityTableQuery[DependencyModel, DependencyTable](tag => new DependencyTable(tag))
  val Parameters = NameableEntityTableQuery[ParameterModel, ParameterTable](tag => new ParameterTable(tag))

  def createSchema(implicit sess: Session) = {
    logger.info("Creating schema ... ")
    (BlueprintReferences.ddl ++
      Clusters.ddl ++
      DefaultBlueprints.ddl ++
      DefaultEscalations.ddl ++
      DefaultFilters.ddl ++
      DefaultRoutings.ddl ++
      DefaultScales.ddl ++
      DefaultSlas.ddl ++
      EscalationReferences.ddl ++
      FilterReferences.ddl ++
      RoutingReferences.ddl ++
      ScaleReferences.ddl ++
      Services.ddl ++
      SlaReferences.ddl ++
      BreedReferences.ddl ++
      DefaultBreeds.ddl ++
      EnvironmentVariables.ddl ++
      Ports.ddl ++
      Dependencies.ddl ++
      Parameters.ddl
      ).create
  }

  class DefaultBlueprintTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultBlueprintModel](tag, "default_blueprints") {
    def * = (name, id.?, isAnonymous) <>(DefaultBlueprintModel.tupled, DefaultBlueprintModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def isAnonymous = column[Boolean]("anonymous")

    def idx = index("idx_default_blueprint", name, unique = true)

    def name = column[String]("name")
  }

  class BlueprintReferenceTable(tag: Tag) extends NameableEntityTable[BlueprintReferenceModel](tag, "blueprint_references") {
    def * = (name, id.?, isDefinedInline) <>(BlueprintReferenceModel.tupled, BlueprintReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    //TODO add key to parent object, which is ???

    def name = column[String]("name")

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def idx = index("idx_blueprint_reference", name, unique = true)
  }

  class ClusterTable(tag: Tag) extends NameableEntityTable[ClusterModel](tag, "clusters") {
    def * = (name, blueprintId, slaReference, id.?) <>(ClusterModel.tupled, ClusterModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def blueprintId = column[Int]("blueprint_id")

    def slaReference = column[Option[String]]("sla_reference") //TODO add foreignkey check

    def name = column[String]("name")

    def idx = index("idx_cluster", (name, blueprintId), unique = true)
  }

  class ServiceTable(tag: Tag) extends EntityTable[ServiceModel](tag, "services") {
    def * = (clusterId, breedReferenceName, routingReferenceName, scaleReferenceName, id.?) <>(ServiceModel.tupled, ServiceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def clusterId = column[Int]("clusterid") //TODO add foreignkey check

    def breedReferenceName = column[String]("breed_reference")

    def routingReferenceName = column[Option[String]]("routing_reference")

    def scaleReferenceName = column[Option[String]]("scale_reference")
  }

  class SlaReferenceTable(tag: Tag) extends NameableEntityTable[SlaReferenceModel](tag, "sla_references") {
    def * = (name, id.?, isDefinedInline) <>(SlaReferenceModel.tupled, SlaReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def isDefinedInline = column[Boolean]("is_defined_inline")

    //def idx = index("idx_sla_references", (name, clusterId) , unique = true)
  }

  class DefaultSlaTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultSlaModel](tag, "default_slas") {
    def * = (name, slaType, id.?, isAnonymous) <>(DefaultSlaModel.tupled, DefaultSlaModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def slaType = column[String]("sla_type")

    def isAnonymous = column[Boolean]("anonymous")

    def idx = index("idx_default_sla", name, unique = true)

    def name = column[String]("name")
  }

  class EscalationReferenceTable(tag: Tag) extends NameableEntityTable[EscalationReferenceModel](tag, "escalation_references") {
    def * = (name, slaId, slaRefId, id.?, isDefinedInline) <>(EscalationReferenceModel.tupled, EscalationReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def idx = index("idx_escalation_references", (name, slaId, slaRefId), unique = true)

    def slaId = column[Option[Int]]("sla_id") //TODO add foreignkey check

    def slaRefId = column[Option[Int]]("sla_ref_id") //TODO add foreignkey check

    def name = column[String]("name")
  }

  class DefaultEscalationTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultEscalationModel](tag, "default_escalations") {
    def * = (name, escalationType, id.?, isAnonymous) <>(DefaultEscalationModel.tupled, DefaultEscalationModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def escalationType = column[String]("escalation_type")

    def isAnonymous = column[Boolean]("anonymous")

    def name = column[String]("name")

    def idx = index("idx_default_escalation", name, unique = true)
  }

  class ScaleReferenceTable(tag: Tag) extends NameableEntityTable[ScaleReferenceModel](tag, "scale_references") {
    def * = (name, id.?, isDefinedInline) <>(ScaleReferenceModel.tupled, ScaleReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def isDefinedInline = column[Boolean]("is_defined_inline")

    //def idx = index("idx_sla_reference", name, unique = true)
  }

  class DefaultScaleTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultScaleModel](tag, "default_scales") {
    def * = (name, cpu, memory, instances, id.?, isAnonymous) <>(DefaultScaleModel.tupled, DefaultScaleModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def cpu = column[Double]("cpu")

    def memory = column[Double]("memory")

    def instances = column[Int]("instances")

    def isAnonymous = column[Boolean]("anonymous")

    def idx = index("idx_default_scala", name, unique = true)

    def name = column[String]("name")
  }

  class RoutingReferenceTable(tag: Tag) extends NameableEntityTable[RoutingReferenceModel](tag, "routing_references") {
    def * = (name, id.?, isDefinedInline) <>(RoutingReferenceModel.tupled, RoutingReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def idx = index("idx_routing_reference", name, unique = true)
  }

  class DefaultRoutingTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultRoutingModel](tag, "default_routings") {
    def * = (name, weight, id.?, isAnonymous) <>(DefaultRoutingModel.tupled, DefaultRoutingModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def weight = column[Option[Int]]("weight")

    def isAnonymous = column[Boolean]("anonymous")

    def name = column[String]("name")

    def idx = index("idx_default_routing", name, unique = true)
  }

  class FilterReferenceTable(tag: Tag) extends NameableEntityTable[FilterReferenceModel](tag, "filter_references") {
    def * = (name, id.?, routingId, isDefinedInline) <>(FilterReferenceModel.tupled, FilterReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc)

    def name = column[String]("name")

    def routingId = column[Int]("routing_id") //TODO add foreignkey check

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def idx = index("idx_filter_reference", (name, routingId), unique = true)
  }

  class DefaultFilterTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultFilterModel](tag, "default_filters") {
    def * = (name, condition, id.?, isAnonymous) <>(DefaultFilterModel.tupled, DefaultFilterModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def condition = column[String]("condition")

    def isAnonymous = column[Boolean]("anonymous")

    def idx = index("idx_default_filter", name, unique = true)
  }

  class BreedReferenceTable(tag: Tag) extends NameableEntityTable[BreedReferenceModel](tag, "breed_references") {
    def * = (name, id.?, isDefinedInline) <>(BreedReferenceModel.tupled, BreedReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def isDefinedInline = column[Boolean]("is_defined_inline")

    //def idx = index("idx_reference_breed", name, unique = true)

  }

  class DefaultBreedTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultBreedModel](tag, "default_breeds") {
    def * = (name, deployable, id.?, isAnonymous) <>(DefaultBreedModel.tupled, DefaultBreedModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def deployable = column[String]("deployable")

    def isAnonymous = column[Boolean]("anonymous")

    def name = column[String]("name")

    def idx = index("idx_default_breed", name, unique = true)
  }

  class EnvironmentVariableTable(tag: Tag) extends NameableEntityTable[EnvironmentVariableModel](tag, "environment_variables") {
    def * = (name, alias, value, direction, id.?, breedId) <>(EnvironmentVariableModel.tupled, EnvironmentVariableModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def alias = column[Option[String]]("alias")

    def value = column[Option[String]]("value")

    def direction = column[Trait.Direction.Value]("direction")

    def name = column[String]("name")

    def breedId = column[Int]("breed_id") //TODO add foreignkey check

    def idx = index("idx_environment_variables", (name, breedId), unique = true)
  }

  class PortTable(tag: Tag) extends NameableEntityTable[PortModel](tag, "ports") {
    def * = (name, alias, portType, value, direction, id.?, breedId) <>(PortModel.tupled, PortModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def alias = column[Option[String]]("alias")

    def portType = column[PortType]("port_type")

    def value = column[Option[Int]]("value")

    def direction = column[Trait.Direction.Value]("direction")

    def name = column[String]("name")

    def breedId = column[Option[Int]]("breed_id") //TODO add foreignkey check

    def idx = index("idx_ports", (name, breedId), unique = true)
  }

  class DependencyTable(tag: Tag) extends NameableEntityTable[DependencyModel](tag, "breed_dependencies") {
    def * = (name, breedName, id.?, isDefinedInline, parentBreedName) <>(DependencyModel.tupled, DependencyModel.unapply)

    def id = column[Int]("dep_id", O.AutoInc, O.PrimaryKey)

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def idx = index("idx_breed_dependencies", (name, breedName, parentBreedName), unique = true)

    def name = column[String]("name")

    def breedName = column[String]("breed_name")

    def parentBreedName = column[String]("parent_breed_name")

    //    def breed: ForeignKeyQuery[BreedModel.Breeds, BreedModel] =
    //      foreignKey("dep_breed_fk", breedName, TableQuery[BreedModel.Breeds])(_.name)
  }

  class ParameterTable(tag: Tag) extends NameableEntityTable[ParameterModel](tag, "parameters") {
    def * = (name, stringValue, intValue, doubleValue, parameterType, id.?, parentType, parentName) <>(ParameterModel.tupled, ParameterModel.unapply)

    def id = column[Int]("dep_id", O.AutoInc, O.PrimaryKey)

    def stringValue = column[Option[String]]("string_value")

    def intValue = column[Int]("int_value")

    def doubleValue = column[Double]("double_value")

    def parameterType = column[ParameterType]("parameter_type")

    def name = column[String]("name")

    def parentType = column[ParameterParentType]("parent_type")

    def parentName = column[String]("parent_name")

    def idx = index("idx_parameters", (name, parentType, parentName), unique = true)

    //    def breed: ForeignKeyQuery[BreedModel.Breeds, BreedModel] =
    //      foreignKey("dep_breed_fk", breedName, TableQuery[BreedModel.Breeds])(_.name)
  }


}
