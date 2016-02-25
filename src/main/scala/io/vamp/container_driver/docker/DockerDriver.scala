package io.vamp.container_driver.docker

import io.vamp.container_driver._
import io.vamp.container_driver.notification.ContainerDriverNotificationProvider
import io.vamp.model.artifact._

import scala.concurrent.{ ExecutionContext, Future }

class DockerDriver(ec: ExecutionContext) extends ContainerDriver with ContainerDriverNotificationProvider {

  def info: Future[ContainerInfo] = ???

  def all: Future[List[ContainerService]] = ???

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any] = ???

  def undeploy(deployment: Deployment, service: DeploymentService): Future[Any] = ???
}
