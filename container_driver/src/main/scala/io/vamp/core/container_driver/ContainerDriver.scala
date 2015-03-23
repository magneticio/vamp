package io.vamp.core.container_driver

import io.vamp.core.model.artifact._

import scala.concurrent.Future


case class ContainerServer(id: String, host: String, ports: List[Int], deployed: Boolean)

case class ContainerService(matching: (Deployment, Breed) => Boolean, scale: DefaultScale, servers: List[ContainerServer])

trait ContainerDriver {

  def all: Future[List[ContainerService]]

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any]

  def undeploy(deployment: Deployment, service: DeploymentService): Future[Any]
}
