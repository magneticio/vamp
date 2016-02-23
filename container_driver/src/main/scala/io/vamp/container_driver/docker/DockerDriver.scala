package io.vamp.container_driver.docker

import io.vamp.container_driver._
import io.vamp.container_driver.notification.ContainerDriverNotificationProvider
import io.vamp.model.artifact._

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Docker driver
 *
 * Docker clients: https://docs.docker.com/engine/reference/api/remote_api_client_libraries/
 * Seems that Java clients are more up to date than Scala.
 *
 */
class DockerDriver(ec: ExecutionContext) extends ContainerDriver with ContainerDriverNotificationProvider {

  def info: Future[ContainerInfo] = ???

  def all: Future[List[ContainerService]] = ???

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any] = ???

  def undeploy(deployment: Deployment, service: DeploymentService): Future[Any] = ???
}
