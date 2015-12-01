package io.vamp.container_driver

import io.vamp.model.artifact._

import scala.concurrent.Future

case class ContainerInfo(`type`: String, container: Any)

case class ContainerService(matching: (Deployment, Breed) ⇒ Boolean, scale: DefaultScale, instances: List[ContainerInstance])

case class ContainerInstance(name: String, host: String, ports: List[Int], deployed: Boolean)

trait ContainerDriver {

  def info: Future[ContainerInfo]

  def all: Future[List[ContainerService]]

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any]

  def undeploy(deployment: Deployment, service: DeploymentService): Future[Any]
}
