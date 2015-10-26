package io.vamp.gateway_driver

import io.vamp.model.artifact._

import scala.concurrent.Future

case class DeploymentRoutes(clusterRoutes: List[ClusterGateway], endpointRoutes: List[EndpointRoute])

trait DeploymentRoute {
  def port: Int

  def services: List[GatewayService]
}

case class ClusterGateway(matching: (Deployment, DeploymentCluster, Port) ⇒ Boolean, port: Int, services: List[GatewayService]) extends DeploymentRoute

case class GatewayService(matching: (DeploymentService) ⇒ Boolean, weight: Int, servers: List[Server], filters: List[Filter])

case class EndpointRoute(matching: (Deployment, Option[Port]) ⇒ Boolean, port: Int, services: List[GatewayService]) extends DeploymentRoute

trait RouterDriver {

  def info: Future[Any]

  def all: Future[DeploymentRoutes]

  def create(deployment: Deployment, cluster: DeploymentCluster, port: Port, update: Boolean): Future[Any]

  def remove(deployment: Deployment, cluster: DeploymentCluster, port: Port): Future[Any]

  def create(deployment: Deployment, port: Port, update: Boolean): Future[Any]

  def remove(deployment: Deployment, port: Port): Future[Any]
}
