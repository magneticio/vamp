package io.vamp.container_driver.kubernetes

import io.vamp.common.Artifact
import io.vamp.common.util.HashUtil
import io.vamp.container_driver.ContainerDriver
import io.vamp.model.artifact._

import scala.util.matching.Regex

object KubernetesContainerDriver {
  val config = "vamp.container-driver.kubernetes"
}

trait KubernetesContainerDriver extends ContainerDriver {

  protected def k8sClient: K8sClient

  protected val nameDelimiter = "-"

  protected val idMatcher: Regex = """^[a-z0-9][a-z0-9-]*$""".r

  protected def workflowNamePrefix: String

  protected def appId(workflow: Workflow): String = s"$workflowNamePrefix${artifactName2Id(workflow)}"

  protected def appId(deployment: Deployment, breed: Breed): String = s"${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  protected def artifactName2Id(artifact: Artifact): String = string2Id(artifact.name)

  protected def string2Id(id: String): String = id match {
    case idMatcher(_*) if id.length < 32 ⇒ id
    case _                               ⇒ HashUtil.hexSha1(id).substring(0, 20)
  }
}
