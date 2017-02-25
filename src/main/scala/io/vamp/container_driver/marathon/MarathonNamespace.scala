package io.vamp.container_driver.marathon

import io.vamp.common.{ Config, NamespaceProvider }
import io.vamp.container_driver.ContainerDriver
import io.vamp.model.artifact.{ Breed, Deployment, Workflow }

trait MarathonNamespace {
  this: ContainerDriver with NamespaceProvider ⇒

  private val workflowNamePrefix = Config.string("vamp.container-driver.marathon.workflow-name-prefix")()

  protected def appId(workflow: Workflow): String = {
    s"$workflowNamePrefix${artifactName2Id(workflow)}"
  }

  protected def appId(deployment: Deployment, breed: Breed): String = {
    s"$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"
  }
}
