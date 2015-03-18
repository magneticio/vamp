package io.magnetic.vamp_core.persistence.slick.components

import java.sql.Timestamp
import java.time.OffsetDateTime

import io.magnetic.vamp_core.model.artifact.Trait
import io.magnetic.vamp_core.persistence.slick.extension.{VampTableQueries, VampTables}
import io.magnetic.vamp_core.persistence.slick.model.DeploymentStateType.DeploymentStateType
import io.magnetic.vamp_core.persistence.slick.model.EnvironmentVariableParentType.EnvironmentVariableParentType
import io.magnetic.vamp_core.persistence.slick.model.ParameterParentType.ParameterParentType
import io.magnetic.vamp_core.persistence.slick.model.ParameterType.ParameterType
import io.magnetic.vamp_core.persistence.slick.model.PortParentType.PortParentType
import io.magnetic.vamp_core.persistence.slick.model.PortType.PortType
import io.magnetic.vamp_core.persistence.slick.model._
import io.strongtyped.active.slick.Profile

import scala.language.implicitConversions
import scala.slick.jdbc.meta.MTable
import scala.slick.util.Logging

trait Schema extends Logging {
  this: VampTables with VampTableQueries with Profile =>

  import jdbcDriver.simple._
  import io.magnetic.vamp_core.persistence.slick.model.Implicits._

  val DefaultBlueprints = AnonymousNameableEntityTableQuery[DefaultBlueprintModel, DefaultBlueprintTable](tag => new DefaultBlueprintTable(tag))
  val BlueprintReferences = DeployableNameEntityTableQuery[BlueprintReferenceModel, BlueprintReferenceTable](tag => new BlueprintReferenceTable(tag))
  val Clusters = DeployableNameEntityTableQuery[ClusterModel, ClusterTable](tag => new ClusterTable(tag))
  val Services = EntityTableQuery[ServiceModel, ServiceTable](tag => new ServiceTable(tag))
  val SlaReferences = DeployableNameEntityTableQuery[SlaReferenceModel, SlaReferenceTable](tag => new SlaReferenceTable(tag))
  val DefaultSlas = AnonymousNameableEntityTableQuery[DefaultSlaModel, DefaultSlaTable](tag => new DefaultSlaTable(tag))
  val EscalationReferences = DeployableNameEntityTableQuery[EscalationReferenceModel, EscalationReferenceTable](tag => new EscalationReferenceTable(tag))
  val DefaultEscalations = AnonymousNameableEntityTableQuery[DefaultEscalationModel, DefaultEscalationTable](tag => new DefaultEscalationTable(tag))
  val ScaleReferences = DeployableNameEntityTableQuery[ScaleReferenceModel, ScaleReferenceTable](tag => new ScaleReferenceTable(tag))
  val DefaultScales = AnonymousNameableEntityTableQuery[DefaultScaleModel, DefaultScaleTable](tag => new DefaultScaleTable(tag))
  val RoutingReferences = DeployableNameEntityTableQuery[RoutingReferenceModel, RoutingReferenceTable](tag => new RoutingReferenceTable(tag))
  val DefaultRoutings = AnonymousNameableEntityTableQuery[DefaultRoutingModel, DefaultRoutingTable](tag => new DefaultRoutingTable(tag))
  val FilterReferences = DeployableNameEntityTableQuery[FilterReferenceModel, FilterReferenceTable](tag => new FilterReferenceTable(tag))
  val DefaultFilters = AnonymousNameableEntityTableQuery[DefaultFilterModel, DefaultFilterTable](tag => new DefaultFilterTable(tag))
  val BreedReferences = DeployableNameEntityTableQuery[BreedReferenceModel, BreedReferenceTable](tag => new BreedReferenceTable(tag))
  val DefaultBreeds = AnonymousNameableEntityTableQuery[DefaultBreedModel, DefaultBreedTable](tag => new DefaultBreedTable(tag))
  val EnvironmentVariables = DeployableNameEntityTableQuery[EnvironmentVariableModel, EnvironmentVariableTable](tag => new EnvironmentVariableTable(tag))
  val Ports = DeployableNameEntityTableQuery[PortModel, PortTable](tag => new PortTable(tag))
  val Dependencies = DeployableNameEntityTableQuery[DependencyModel, DependencyTable](tag => new DependencyTable(tag))
  val Parameters = DeployableNameEntityTableQuery[ParameterModel, ParameterTable](tag => new ParameterTable(tag))
  val TraitNameParameters = DeployableNameEntityTableQuery[TraitNameParameterModel, TraitNameParameterTable](tag => new TraitNameParameterTable(tag))
  val VampPersistenceMetaDatas = EntityTableQuery[VampPersistenceMetaDataModel, VampPersistenceMetaDataTable](tag => new VampPersistenceMetaDataTable(tag))
  val DeploymentServers = DeployableNameEntityTableQuery[DeploymentServerModel, DeploymentServerTable](tag => new DeploymentServerTable(tag))
  val DeploymentServices = DeployableNameEntityTableQuery[DeploymentServiceModel, DeploymentServiceTable](tag => new DeploymentServiceTable(tag))
  val DeploymentClusters = DeployableNameEntityTableQuery[DeploymentClusterModel, DeploymentClusterTable](tag => new DeploymentClusterTable(tag))
  val Deployments = NameableEntityTableQuery[DeploymentModel, DeploymentTable](tag => new DeploymentTable(tag))
  val ServerPorts = EntityTableQuery[ServerPortModel, ServerPortTable](tag => new ServerPortTable(tag))
  val DeploymentServiceDependencies = EntityTableQuery[DeploymentServiceDependencyModel, DeploymentServiceDependencyTable](tag => new DeploymentServiceDependencyTable(tag))
  val ClusterRoutes = EntityTableQuery[ClusterRouteModel, ClusterRouteTable](tag => new ClusterRouteTable(tag))

  private def tableQueries  = List(
    Ports,
    EnvironmentVariables,
    Parameters,
    TraitNameParameters,
    DefaultFilters,
    FilterReferences,
    DefaultRoutings,
    RoutingReferences,
    DefaultEscalations,
    EscalationReferences,
    DefaultSlas,
    SlaReferences,
    DefaultScales,
    ScaleReferences,
    Dependencies,
    DefaultBreeds,
    BreedReferences,
    Services,
    Clusters,
    DefaultBlueprints,
    BlueprintReferences,
    ServerPorts,
    DeploymentServers,
    DeploymentServiceDependencies,
    DeploymentServices,
    DeploymentClusters,
    ClusterRoutes,
    Deployments,
    VampPersistenceMetaDatas
  )

  private def schemaVersion : Int = 1

  def upgradeSchema(implicit sess: Session) = {
    getCurrentSchemaVersion match {
      case version if version == schemaVersion =>
        // Up to date
      case version if version == 0 =>
        createSchema
    }
  }

  private def createSchema(implicit sess: Session) = {
    logger.info("Creating schema ...")
    for (tableQuery <- tableQueries) {
      logger.info(tableQuery.ddl.createStatements.mkString)
      tableQuery.ddl.create
    }
    VampPersistenceMetaDatas.add(VampPersistenceMetaDataModel(schemaVersion=schemaVersion))
    logger.info("Schema created")
  }

  private def getCurrentSchemaVersion(implicit sess: Session) : Int =
    MTable.getTables("vamp-meta-data").firstOption match {
      case Some(_) => VampPersistenceMetaDatas.sortBy(_.id.desc).firstOption match {
        case Some(metaData) => metaData.schemaVersion
        case None => 0
      }
      case None => 0
    }

   def destroySchema(implicit sess: Session) = {
     if (getCurrentSchemaVersion == schemaVersion) {
     logger.info("Removing everything from the schema ...")
     for (tableQuery <- tableQueries.reverse) {
       logger.info(tableQuery.ddl.dropStatements.mkString)
       tableQuery.ddl.drop
     }
     logger.info("Schema cleared")
   } }

  class VampPersistenceMetaDataTable(tag: Tag) extends EntityTable[VampPersistenceMetaDataModel](tag, "vamp-meta-data") {
    def * = (id.?, schemaVersion, created) <>(VampPersistenceMetaDataModel.tupled, VampPersistenceMetaDataModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def schemaVersion = column[Int]("schema_version")

    def created = column[Timestamp]("created")
  }


  class DefaultBlueprintTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultBlueprintModel](tag, "default_blueprints") {
    def * = (deploymentId, name, id.?, isAnonymous) <>(DefaultBlueprintModel.tupled, DefaultBlueprintModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def isAnonymous = column[Boolean]("anonymous")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def idx = index("idx_default_blueprint", (name, deploymentId), unique = true)

    def name = column[String]("name")
  }

  class BlueprintReferenceTable(tag: Tag) extends DeployableEntityTable[BlueprintReferenceModel](tag, "blueprint_references") {
    def * = (deploymentId, name, id.?, isDefinedInline) <>(BlueprintReferenceModel.tupled, BlueprintReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def idx = index("idx_blueprint_reference", (name , deploymentId), unique = true)
  }

  class ClusterTable(tag: Tag) extends DeployableEntityTable[ClusterModel](tag, "clusters") {
    def * = (deploymentId, name, blueprintId, slaReference, id.?) <>(ClusterModel.tupled, ClusterModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def blueprintId = column[Int]("blueprint_id")

    def slaReference = column[Option[String]]("sla_reference") //TODO add foreignkey check

    def name = column[String]("name")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def idx = index("idx_cluster", (name, blueprintId, deploymentId), unique = true)
  }

  class ServiceTable(tag: Tag) extends EntityTable[ServiceModel](tag, "services") {
    def * = (deploymentId, clusterId, breedReferenceName, routingReferenceName, scaleReferenceName, id.?) <>(ServiceModel.tupled, ServiceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def clusterId = column[Int]("clusterid") //TODO add foreignkey check

    def breedReferenceName = column[String]("breed_reference")

    def routingReferenceName = column[Option[String]]("routing_reference")

    def scaleReferenceName = column[Option[String]]("scale_reference")

    def deploymentId = column[Option[Int]]("deployment_fk")

  }

  class SlaReferenceTable(tag: Tag) extends DeployableEntityTable[SlaReferenceModel](tag, "sla_references") {
    def * = (deploymentId, name, id.?, isDefinedInline) <>(SlaReferenceModel.tupled, SlaReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def deploymentId = column[Option[Int]]("deployment_fk")
    //def idx = index("idx_sla_references", (name, clusterId) , unique = true)
  }

  class DefaultSlaTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultSlaModel](tag, "default_slas") {
    def * = (deploymentId, name, slaType, id.?, isAnonymous) <>(DefaultSlaModel.tupled, DefaultSlaModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def slaType = column[String]("sla_type")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def isAnonymous = column[Boolean]("anonymous")

    def idx = index("idx_default_sla", (name, deploymentId), unique = true)

    def name = column[String]("name")
  }

  class EscalationReferenceTable(tag: Tag) extends DeployableEntityTable[EscalationReferenceModel](tag, "escalation_references") {
    def * = (deploymentId, name, slaId, slaRefId, id.?, isDefinedInline) <>(EscalationReferenceModel.tupled, EscalationReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def idx = index("idx_escalation_references", (name, slaId, slaRefId, deploymentId), unique = true)

    def deploymentId = column[Option[Int]]("deployment_fk")

    def slaId = column[Option[Int]]("sla_id") //TODO add foreignkey check

    def slaRefId = column[Option[Int]]("sla_ref_id") //TODO add foreignkey check

    def name = column[String]("name")
  }

  class DefaultEscalationTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultEscalationModel](tag, "default_escalations") {
    def * = (deploymentId, name, escalationType, id.?, isAnonymous) <>(DefaultEscalationModel.tupled, DefaultEscalationModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def escalationType = column[String]("escalation_type")

    def isAnonymous = column[Boolean]("anonymous")

    def idx = index("idx_default_escalation", (name, deploymentId) , unique = true)

    def deploymentId = column[Option[Int]]("deployment_fk")

    def name = column[String]("name")
  }

  class ScaleReferenceTable(tag: Tag) extends DeployableEntityTable[ScaleReferenceModel](tag, "scale_references") {
    def * = (deploymentId, name, id.?, isDefinedInline) <>(ScaleReferenceModel.tupled, ScaleReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def deploymentId = column[Option[Int]]("deployment_fk")

    //def idx = index("idx_sla_reference", name, unique = true)
  }

  class DefaultScaleTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultScaleModel](tag, "default_scales") {
    def * = (deploymentId, name, cpu, memory, instances, id.?, isAnonymous) <>(DefaultScaleModel.tupled, DefaultScaleModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def cpu = column[Double]("cpu")

    def memory = column[Double]("memory")

    def instances = column[Int]("instances")

    def isAnonymous = column[Boolean]("anonymous")

    def idx = index("idx_default_scala", (name, deploymentId), unique = true)

    def deploymentId = column[Option[Int]]("deployment_fk")

    def name = column[String]("name")
  }

  class RoutingReferenceTable(tag: Tag) extends DeployableEntityTable[RoutingReferenceModel](tag, "routing_references") {
    def * = (deploymentId, name, id.?, isDefinedInline) <>(RoutingReferenceModel.tupled, RoutingReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def idx = index("idx_routing_reference", (name, deploymentId), unique = true)

    def name = column[String]("name")

    def deploymentId = column[Option[Int]]("deployment_fk")
  }

  class DefaultRoutingTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultRoutingModel](tag, "default_routings") {
    def * = (deploymentId, name, weight, id.?, isAnonymous) <>(DefaultRoutingModel.tupled, DefaultRoutingModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def weight = column[Option[Int]]("weight")

    def isAnonymous = column[Boolean]("anonymous")

    def idx = index("idx_default_routing", (name, deploymentId), unique = true)

    def deploymentId = column[Option[Int]]("deployment_fk")

    def name = column[String]("name")
  }

  class FilterReferenceTable(tag: Tag) extends DeployableEntityTable[FilterReferenceModel](tag, "filter_references") {
    def * = (deploymentId, name, id.?, routingId, isDefinedInline) <>(FilterReferenceModel.tupled, FilterReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc)

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def idx = index("idx_filter_reference", (name, routingId, deploymentId), unique = true)

    def deploymentId = column[Option[Int]]("deployment_fk")

    def name = column[String]("name")

    def routingId = column[Int]("routing_id") //TODO add foreignkey check
  }

  class DefaultFilterTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultFilterModel](tag, "default_filters") {
    def * = (deploymentId, name, condition, id.?, isAnonymous) <>(DefaultFilterModel.tupled, DefaultFilterModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def condition = column[String]("condition")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def isAnonymous = column[Boolean]("anonymous")

    def idx = index("idx_default_filter", name, unique = true)
  }

  class BreedReferenceTable(tag: Tag) extends DeployableEntityTable[BreedReferenceModel](tag, "breed_references") {
    def * = (deploymentId, name, id.?, isDefinedInline) <>(BreedReferenceModel.tupled, BreedReferenceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def deploymentId = column[Option[Int]]("deployment_fk")

    //def idx = index("idx_reference_breed", name, unique = true)

  }

  class DefaultBreedTable(tag: Tag) extends AnonymousNameableEntityTable[DefaultBreedModel](tag, "default_breeds") {
    def * = (deploymentId, name, deployable, id.?, isAnonymous) <>(DefaultBreedModel.tupled, DefaultBreedModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def deployable = column[String]("deployable")

    def isAnonymous = column[Boolean]("anonymous")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def name = column[String]("name")

    def idx = index("idx_default_breed", (name, deploymentId), unique = true)
  }

  class EnvironmentVariableTable(tag: Tag) extends DeployableEntityTable[EnvironmentVariableModel](tag, "environment_variables") {
    def * = (deploymentId, name, alias, value, direction, id.?, parentId, parentType) <>(EnvironmentVariableModel.tupled, EnvironmentVariableModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def alias = column[Option[String]]("alias")

    def value = column[Option[String]]("env_value")

    def direction = column[Trait.Direction.Value]("env_direction")

    def name = column[String]("name")

    def parentId = column[Option[Int]]("parent_id") //TODO add foreignkey check

    def parentType = column[Option[EnvironmentVariableParentType]]("parent_type")

    def deploymentId = column[Option[Int]]("deployment_fk")


    //def idx = index("idx_environment_variables", (name, parent), unique = true)
  }

  class PortTable(tag: Tag) extends DeployableEntityTable[PortModel](tag, "ports") {
    def * = (deploymentId, name, alias, portType, value, direction, id.?, parentId, parentType) <>(PortModel.tupled, PortModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def alias = column[Option[String]]("alias")

    def portType = column[PortType]("port_type")

    def value = column[Option[Int]]("port_value")

    def direction = column[Trait.Direction.Value]("port_direction")

    def idx = index("idx_ports", (name, parentId, parentType), unique = true)

    def name = column[String]("name")

    def parentId = column[Option[Int]]("parent_id") //TODO add foreignkey check

    def parentType = column[Option[PortParentType]]("parent_type")

    def deploymentId = column[Option[Int]]("deployment_fk")

  }

  class DependencyTable(tag: Tag) extends DeployableEntityTable[DependencyModel](tag, "breed_dependencies") {
    def * = (deploymentId, name, breedName, id.?, isDefinedInline, parentId) <>(DependencyModel.tupled, DependencyModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def isDefinedInline = column[Boolean]("is_defined_inline")

    def name = column[String]("name")

    def breedName = column[String]("breed_name")

    def parentId = column[Int]("parent_id")

    def idx = index("idx_breed_dependencies", (name, breedName, parentId, deploymentId), unique = true)

    def deploymentId = column[Option[Int]]("deployment_fk")


    //    def breed: ForeignKeyQuery[BreedModel.Breeds, BreedModel] =
    //      foreignKey("dep_breed_fk", breedName, TableQuery[BreedModel.Breeds])(_.name)
  }

  class ParameterTable(tag: Tag) extends DeployableEntityTable[ParameterModel](tag, "parameters") {
    def * = (deploymentId, name, stringValue, intValue, doubleValue, parameterType, id.?, parentType, parentId) <>(ParameterModel.tupled, ParameterModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def stringValue = column[Option[String]]("string_value")

    def intValue = column[Int]("int_value")

    def doubleValue = column[Double]("double_value")

    def parameterType = column[ParameterType]("parameter_type")

    def idx = index("idx_parameters", (name, parentType, parentId), unique = true)

    def name = column[String]("name")

    def parentType = column[ParameterParentType]("parent_type")

    def parentId = column[Int]("parent_id")

    def deploymentId = column[Option[Int]]("deployment_fk")


    //    def breed: ForeignKeyQuery[BreedModel.Breeds, BreedModel] =
    //      foreignKey("dep_breed_fk", breedName, TableQuery[BreedModel.Breeds])(_.name)
  }

  class TraitNameParameterTable(tag: Tag) extends DeployableEntityTable[TraitNameParameterModel](tag, "trait_name_parameters") {
    def * = (deploymentId, id.?, name, scope, group, stringValue, groupId, parentId) <>(TraitNameParameterModel.tupled, TraitNameParameterModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def scope = column[Option[String]]("param_scope")

    def group = column[Option[Trait.Name.Group.Value]]("param_group")

    def stringValue = column[Option[String]]("string_value")

    def groupId = column[Option[Int]]("group_id")

    def parentId = column[Int]("parent_id")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def idx = index("idx_trait_name_parameters", (name, scope, group, parentId, deploymentId), unique = true)

  }

  class DeploymentServerTable(tag: Tag) extends DeployableEntityTable[DeploymentServerModel](tag, "deployment_servers") {
    def * = (deploymentId, serviceId, id.?, name,host) <>(DeploymentServerModel.tupled, DeploymentServerModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def serviceId = column[Int]("service_fk")   // Add foreign_key

    def name = column[String]("name")

    def host = column[String]("host_name")

    def deploymentId = column[Option[Int]]("deployment_fk")   // Add foreign_key

    def idx = index("idx_deployment_servers", (name, deploymentId), unique = true)
  }

  class DeploymentServiceTable(tag: Tag) extends DeployableEntityTable[DeploymentServiceModel](tag, "deployment_services") {
    def * = (deploymentId, clusterId, id.?, name, breedId, scaleId, routingId, deploymentStateType, deploymentTime, message ) <>(DeploymentServiceModel.tupled, DeploymentServiceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def clusterId = column[Int]("cluster_fk")   // Add foreign_key

    def name = column[String]("name")

    def breedId = column[Int]("breed_id")

    def scaleId = column[Int]("scale_id")

    def routingId = column[Int]("routing_id")

    def deploymentId = column[Option[Int]]("deployment_fk")   // Add foreign_key

    def deploymentStateType = column[DeploymentStateType]("deployment_state")

    def deploymentTime= column[OffsetDateTime]("deployment_time")

    def message = column[Option[String]]("message")

    def idx = index("idx_deployment_services", (name, deploymentId), unique = true)
  }

  class DeploymentClusterTable(tag: Tag) extends DeployableEntityTable[DeploymentClusterModel](tag, "deployment_clusters") {
    def * = (deploymentId, id.?, name, slaReference) <>(DeploymentClusterModel.tupled, DeploymentClusterModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def slaReference = column[Option[String]]("sla_reference")

    def deploymentId = column[Option[Int]]("deployment_fk")   // Add foreign_key

    def idx = index("idx_deployment_clusters", (name, deploymentId), unique = true)
  }

  class DeploymentTable(tag: Tag) extends NameableEntityTable[DeploymentModel](tag, "deployments") {
    def * = (id.?, name) <>(DeploymentModel.tupled, DeploymentModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def idx = index("idx_deployments", name, unique = true)
  }

  class ServerPortTable(tag: Tag) extends EntityTable[ServerPortModel](tag, "server_ports") {
    def * = (id.?, portIn, portOut, serverId) <>(ServerPortModel.tupled, ServerPortModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def portIn = column[Int]("port_in")

    def portOut = column[Int]("port_out")

    def serverId = column[Int]("server_fk")   // Add foreign_key
  }


  class DeploymentServiceDependencyTable(tag: Tag) extends EntityTable[DeploymentServiceDependencyModel](tag, "deployment_services_dependencies") {
    def * = (id.?, name, value, serviceId) <>(DeploymentServiceDependencyModel.tupled, DeploymentServiceDependencyModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("dep_name")

    def value = column[String]("dep_value")

    def serviceId = column[Int]("service_fk")   // Add foreign_key
  }

  class ClusterRouteTable(tag: Tag) extends EntityTable[ClusterRouteModel](tag, "cluster_routes") {
    def * = (id.?, portIn, portOut, clusterId) <>(ClusterRouteModel.tupled, ClusterRouteModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def portIn = column[Int]("port_in")

    def portOut = column[Int]("port_out")

    def clusterId = column[Int]("server_fk")   // Add foreign_key
  }

}
