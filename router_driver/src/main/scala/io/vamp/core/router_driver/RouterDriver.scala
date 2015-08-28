package io.vamp.core.router_driver

import io.vamp.core.model.artifact._

import scala.concurrent.Future

case class DeploymentRoutes(clusterRoutes: List[ClusterRoute], endpointRoutes: List[EndpointRoute])

trait DeploymentRoute {
  def port: Int

  def services: List[RouteService]
}

case class ClusterRoute(matching: (Deployment, DeploymentCluster, Port) => Boolean, port: Int, services: List[RouteService]) extends DeploymentRoute

case class RouteService(matching: (DeploymentService) => Boolean, weight: Int, servers: List[Server], filters: List[Filter])

case class EndpointRoute(matching: (Deployment, Option[Port]) => Boolean, port: Int, services: List[RouteService]) extends DeploymentRoute

trait RouterDriver {

  def info: Future[Any]

  def all: Future[DeploymentRoutes]

  def create(deployment: Deployment, cluster: DeploymentCluster, port: Port, update: Boolean): Future[Any]

  def remove(deployment: Deployment, cluster: DeploymentCluster, port: Port): Future[Any]

  def create(deployment: Deployment, port: Port, update: Boolean): Future[Any]

  def remove(deployment: Deployment, port: Port): Future[Any]
}
