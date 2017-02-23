package io.vamp.container_driver

import io.vamp.common.NamespaceResolver
import io.vamp.model.artifact.Deployable

abstract class DeployableType(val types: String*) {
  def matches(deployable: Deployable)(implicit namespaceResolver: NamespaceResolver): Boolean = {
    types.contains(deployable.defaultType())
  }
}

object CommandDeployableType extends DeployableType("command", "cmd")

object RktDeployableType extends DeployableType("container/rkt", "rkt")

object DockerDeployableType extends DeployableType("container/docker", "docker")
