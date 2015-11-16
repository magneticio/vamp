package io.vamp.core.container_driver

import io.vamp.core.model.artifact._

import scala.concurrent.Future

case class ContainerInfo(`type`: String, container: Any)

case class ContainerService(matching: (Deployment, Breed) ⇒ Boolean, scale: DefaultScale, servers: List[ContainerServer])

case class ContainerServer(name: String, host: String, ports: List[Int], deployed: Boolean)

trait ContainerDriver {

  def info: Future[ContainerInfo]

  def all: Future[List[ContainerService]]

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any]

  def undeploy(deployment: Deployment, service: DeploymentService): Future[Any]
}
