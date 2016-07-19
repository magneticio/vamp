package io.vamp.container_driver

import io.vamp.model.artifact.Deployable

abstract class DeployableType(val `type`: String) {
  def matches(some: Deployable) = some.`type` == `type`
}

object DockerDeployable extends DeployableType("container/docker")

object CommandDeployable extends DeployableType("command") {
  override def matches(some: Deployable) = super.matches(some) || some.`type` == "cmd"
}
