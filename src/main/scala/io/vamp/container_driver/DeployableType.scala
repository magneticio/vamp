package io.vamp.container_driver

sealed abstract class DeployableType(val `type`: String) {
  def is(some: String) = some == `type`
}

object DockerDeployable extends DeployableType("container/docker")

object CommandDeployable extends DeployableType("command") {
  override def is(some: String) = super.is(some) || some == "cmd"
}
