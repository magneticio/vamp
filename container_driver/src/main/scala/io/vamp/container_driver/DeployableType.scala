package io.vamp.container_driver

import io.vamp.common.Namespace
import io.vamp.model.artifact.Deployable

abstract class DeployableType(val types: String*) {
  def matches(deployable: Deployable)(implicit namespace: Namespace): Boolean = {
    types.contains(deployable.defaultType())
  }
}

object CommandDeployableType extends DeployableType("command", "cmd")

object RktDeployableType extends DeployableType("container/rkt", "rkt")

object DockerDeployableType extends DeployableType("container/docker", "docker")
