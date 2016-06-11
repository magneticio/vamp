package io.vamp.container_driver

import io.vamp.container_driver.ContainerDriverActor.ContainerDriveMessage

object DockerAppDriver {

  case class DeployDockerApp(app: DockerApp, update: Boolean) extends ContainerDriveMessage

  case class RetrieveDockerApp(app: String) extends ContainerDriveMessage

  case class UndeployDockerApp(app: String) extends ContainerDriveMessage

}