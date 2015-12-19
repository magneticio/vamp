package io.vamp.gateway_driver

import io.vamp.common.crypto.Hash
import io.vamp.model.artifact._

import scala.util.matching.Regex

trait GatewayNameMatcher {

  val nameDelimiter = "_"

  val idMatcher = """^[a-zA-Z0-9][a-zA-Z0-9.\-_]{3,63}$""".r
  val serviceIdMatcher = """^[a-zA-Z0-9][a-zA-Z0-9.\-_:]{1,63}$""".r

  def clusterGatewayNameMatcher(id: String): (Deployment, DeploymentCluster, Port) ⇒ Boolean = { (deployment: Deployment, cluster: DeploymentCluster, port: Port) ⇒ id == clusterGatewayName(deployment, cluster, port) }

  def serviceGatewayNameMatcher(id: String): (DeploymentService) ⇒ Boolean = { (deploymentService: DeploymentService) ⇒ id == artifactName2Id(deploymentService.breed, serviceIdMatcher) }

  def endpointGatewayNameMatcher(id: String): (Deployment, Option[Port]) ⇒ Boolean = (deployment: Deployment, optionalPort: Option[Port]) ⇒ optionalPort match {
    case None       ⇒ isDeploymentEndpoint(id, deployment)
    case Some(port) ⇒ id == endpointGatewayName(deployment, port)
  }

  def clusterGatewayName(deployment: Deployment, cluster: DeploymentCluster, port: Port): String =
    string2Id(s"${deployment.name}$nameDelimiter${cluster.name}$nameDelimiter${port.name}")

  def endpointGatewayName(deployment: Deployment, port: Port): String =
    s"${artifactName2Id(deployment)}$nameDelimiter${port.name}"

  def isDeploymentEndpoint(id: String, deployment: Deployment): Boolean =
    id.startsWith(s"${artifactName2Id(deployment)}$nameDelimiter")

  def artifactName2Id(artifact: Artifact, matcher: Regex = idMatcher) = string2Id(artifact.name, matcher)

  def string2Id(string: String, matcher: Regex = idMatcher) = string match {
    case matcher(_*) ⇒ string
    case _           ⇒ Hash.hexSha1(string)
  }
}
