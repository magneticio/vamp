package io.vamp.core.container_driver

import io.vamp.common.crypto.Hash
import io.vamp.core.container_driver.marathon.api.CreatePortMapping
import io.vamp.core.model.artifact._

abstract class AbstractContainerDriver extends ContainerDriver {

  protected val nameDelimiter = "/"
  protected val idMatcher = """^(([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])\\.)*([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])$""".r

  protected def appId(deployment: Deployment, breed: Breed): String = s"$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  protected def processable(id: String): Boolean = id.split(nameDelimiter).size == 3

  protected def nameMatcher(id: String): (Deployment, Breed) => Boolean = { (deployment: Deployment, breed: Breed) => id == appId(deployment, breed) }

  protected def artifactName2Id(artifact: Artifact) = artifact.name match {
    case idMatcher(_*) => artifact.name
    case _ => Hash.hexSha1(artifact.name).substring(0, 20)
  }

  protected def portMappings(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): List[CreatePortMapping] = {
    service.breed.ports.map(port =>
      port.value match {
        case Some(_) => CreatePortMapping(port.number)
        case None => CreatePortMapping(deployment.ports.find(p => TraitReference(cluster.name, TraitReference.Ports, port.name).toString == p.name).get.number)
      })
  }

  protected def environment(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Map[String, String] =
    service.breed.environmentVariables.map(ev => ev.alias.getOrElse(ev.name) -> deployment.environmentVariables.find(e => TraitReference(cluster.name, TraitReference.EnvironmentVariables, ev.name).toString == e.name).get.value.get).toMap


}
