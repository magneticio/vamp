package io.vamp.core.persistence.slick.components

import java.time.OffsetDateTime

import io.strongtyped.active.slick.Profile
import io.vamp.core.persistence.slick.extension.{VampTableQueries, VampTables}
import io.vamp.core.persistence.slick.model.DeploymentStateType.DeploymentStateType
import io.vamp.core.persistence.slick.model._

import scala.language.implicitConversions
import scala.slick.util.Logging

trait SchemaDeployment extends Logging with SchemaBreed {
  this: VampTables with VampTableQueries with Profile =>

  import Implicits._
  import jdbcDriver.simple._

  val DeploymentServers = DeployableNameEntityTableQuery[DeploymentServerModel, DeploymentServerTable](tag => new DeploymentServerTable(tag))
  val DeploymentServices = DeployableNameEntityTableQuery[DeploymentServiceModel, DeploymentServiceTable](tag => new DeploymentServiceTable(tag))
  val DeploymentClusters = DeployableNameEntityTableQuery[DeploymentClusterModel, DeploymentClusterTable](tag => new DeploymentClusterTable(tag))
  val ServerPorts = EntityTableQuery[ServerPortModel, ServerPortTable](tag => new ServerPortTable(tag))
  val DeploymentServiceDependencies = EntityTableQuery[DeploymentServiceDependencyModel, DeploymentServiceDependencyTable](tag => new DeploymentServiceDependencyTable(tag))
  val ClusterRoutes = EntityTableQuery[ClusterRouteModel, ClusterRouteTable](tag => new ClusterRouteTable(tag))
  val DeploymentHosts = NameableEntityTableQuery[HostModel, HostTable](tag => new HostTable(tag))

  class DeploymentServerTable(tag: Tag) extends DeployableEntityTable[DeploymentServerModel](tag, "deployment_servers") {
    def * = (deploymentId, serviceId, id.?, name, host, deployed) <>(DeploymentServerModel.tupled, DeploymentServerModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def serviceId = column[Int]("service_fk")

    def name = column[String]("name")

    def host = column[String]("host_name")

    def deployed = column[Boolean]("deployed")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def idx = index("idx_deployment_servers", (name, deploymentId, serviceId), unique = true)

    def deployment = foreignKey("deployment_server_deployment_fk", deploymentId, Deployments)(_.id)

    def service = foreignKey("deployment_server_deployment_service_fk", serviceId, DeploymentServices)(_.id)
  }

  class DeploymentServiceTable(tag: Tag) extends DeployableEntityTable[DeploymentServiceModel](tag, "deployment_services") {
    def * = (deploymentId, clusterId, id.?, name, breedId, scaleId, routingId, deploymentStateType, deploymentTime, dialects, message) <>(DeploymentServiceModel.tupled, DeploymentServiceModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def clusterId = column[Int]("cluster_fk")

    def breedId = column[Int]("breed_id")

    def scaleId = column[Option[Int]]("scale_id")

    def routingId = column[Option[Int]]("routing_id")

    def deploymentStateType = column[DeploymentStateType]("deployment_state")

    def deploymentTime = column[OffsetDateTime]("deployment_time")

    def message = column[Option[String]]("message")

    def idx = index("idx_deployment_services", (name, deploymentId), unique = true)

    def name = column[String]("name")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def dialects = column[Array[Byte]]("dialects")

    def deployment = foreignKey("deployment_service_deployment_fk", deploymentId, Deployments)(_.id)

    def breed = foreignKey("deployment_service_breed_fk", breedId, BreedReferences)(_.id)

    def scale = foreignKey("deployment_service_scale_fk", scaleId, ScaleReferences)(_.id)

    def routing = foreignKey("deployment_service_routing_fk", routingId, RoutingReferences)(_.id)

    def cluster = foreignKey("deployment_service_deployment_cluster_fk", clusterId, DeploymentClusters)(_.id)
  }

  class DeploymentClusterTable(tag: Tag) extends DeployableEntityTable[DeploymentClusterModel](tag, "deployment_clusters") {
    def * = (deploymentId, id.?, name, slaReferenceId, dialects) <>(DeploymentClusterModel.tupled, DeploymentClusterModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("name")

    def slaReferenceId = column[Option[Int]]("sla_reference")

    def dialects = column[Array[Byte]]("dialects")

    def deploymentId = column[Option[Int]]("deployment_fk")

    def idx = index("idx_deployment_clusters", (name, deploymentId), unique = true)

    def deployment = foreignKey("deployment_cluster_deployment_fk", deploymentId, Deployments)(_.id)

    def slaRef = foreignKey("deployment_cluster_sla_reference_fk", slaReferenceId, SlaReferences)(_.id)
  }

  class ServerPortTable(tag: Tag) extends EntityTable[ServerPortModel](tag, "server_ports") {
    def * = (id.?, portIn, portOut, serverId) <>(ServerPortModel.tupled, ServerPortModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def portIn = column[Int]("port_in")

    def portOut = column[Int]("port_out")

    def serverId = column[Int]("server_fk")

    def server = foreignKey("server_port_server_fk", serverId, DeploymentServers)(_.id)
  }


  class DeploymentServiceDependencyTable(tag: Tag) extends EntityTable[DeploymentServiceDependencyModel](tag, "deployment_services_dependencies") {
    def * = (id.?, name, value, serviceId) <>(DeploymentServiceDependencyModel.tupled, DeploymentServiceDependencyModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def name = column[String]("dep_name")

    def value = column[String]("dep_value")

    def serviceId = column[Int]("service_fk")

    def service = foreignKey("deployment_dependency_deployment_service_fk", serviceId, DeploymentServices)(_.id)
  }

  class ClusterRouteTable(tag: Tag) extends EntityTable[ClusterRouteModel](tag, "cluster_routes") {
    def * = (id.?, portIn, portOut, clusterId) <>(ClusterRouteModel.tupled, ClusterRouteModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def portIn = column[Int]("port_in")

    def portOut = column[Int]("port_out")

    def clusterId = column[Int]("server_fk")

    def idx = index("idx_cluster_routes", (portIn, clusterId), unique = true)

    def cluster = foreignKey("cluster_route_deployment_cluster_fk", clusterId, DeploymentClusters)(_.id)
  }

  class HostTable(tag: Tag) extends NameableEntityTable[HostModel](tag, "deployment_hosts") {
    def * = (id.?, name, value, deploymentId) <>(HostModel.tupled, HostModel.unapply)

    def id = column[Int]("id", O.AutoInc, O.PrimaryKey)

    def value = column[Option[String]]("host_value")

    def idx = index("idx_deployment_hosts", (name, deploymentId), unique = true)

    def name = column[String]("name")

    def deploymentId = column[Option[Int]]("breed_id")

    def deployment = foreignKey("deployment_host_deployment_fk", deploymentId, Deployments)(_.id)
  }

}
