package io.vamp.container_driver

import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.common.crypto.Hash
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider, UnsupportedDeployableSchema }
import io.vamp.model.artifact._
import io.vamp.model.resolver.DeploymentTraitResolver

case class ContainerPortMapping(containerPort: Int, protocol: String = "tcp", hostPort: Int = 0)

case class Container(docker: Docker, `type`: String = "DOCKER")

case class Docker(image: String, portMappings: List[ContainerPortMapping], parameters: List[DockerParameter], privileged: Boolean = false, network: String = "BRIDGE")

case class DockerParameter(key: String, value: String)

trait ContainerDriver extends DeploymentTraitResolver with ContainerDriverNotificationProvider with ExecutionContextProvider {

  protected val nameDelimiter = "/"
  protected val idMatcher = """^(([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])\\.)*([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])$""".r

  protected def appId(deployment: Deployment, breed: Breed): String = s"$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  protected def processable(id: String): Boolean = id.split(nameDelimiter).length == 3

  protected def nameMatcher(id: String): (Deployment, Breed) ⇒ Boolean = { (deployment: Deployment, breed: Breed) ⇒ id == appId(deployment, breed) }

  protected def artifactName2Id(artifact: Artifact): String = artifact.name match {
    case idMatcher(_*) ⇒ artifact.name
    case _             ⇒ Hash.hexSha1(artifact.name).substring(0, 20)
  }

  protected def portMappings(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): List[ContainerPortMapping] = {
    service.breed.ports.map(port ⇒
      port.value match {
        case Some(_) ⇒ ContainerPortMapping(port.number)
        case None    ⇒ ContainerPortMapping(deployment.ports.find(p ⇒ TraitReference(cluster.name, TraitReference.Ports, port.name).toString == p.name).get.number)
      })
  }

  protected def environment(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Map[String, String] =
    service.environmentVariables.map(ev ⇒ ev.alias.getOrElse(ev.name) -> ev.interpolated.getOrElse("")).toMap

  protected def validateSchemaSupport(schema: String, enum: Enumeration) = {
    if (!enum.values.exists(en ⇒ en.toString.compareToIgnoreCase(schema) == 0))
      throwException(UnsupportedDeployableSchema(schema, enum.values.map(_.toString.toLowerCase).mkString(", ")))
  }

  protected def interpolate[T](deployment: Deployment, service: Option[DeploymentService], dialect: T): T = {
    def visit(any: Any): Any = any match {
      case value: String ⇒ resolve(value, valueFor(deployment, service))
      case list: List[_] ⇒ list.map(visit)
      case map: Map[_, _] ⇒ map.map {
        case (key, value) ⇒ key -> visit(value)
      }
      case _ ⇒ any
    }

    visit(dialect).asInstanceOf[T]
  }
}
