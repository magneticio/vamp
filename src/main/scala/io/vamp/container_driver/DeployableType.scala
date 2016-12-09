package io.vamp.container_driver

import io.vamp.model.artifact.Deployable

abstract class DeployableType(val types: String*) {
  def matches(deployable: Deployable): Boolean = types.contains(deployable.`type`)
}

object CommandDeployable extends DeployableType("command", "cmd")

object RktDeployable extends DeployableType("container/rkt", "rkt")

object DockerDeployable extends DeployableType("container/docker", "docker")
