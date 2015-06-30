package io.vamp.core.container_driver

import io.vamp.common.crypto.Hash
import io.vamp.core.container_driver.docker.DockerPortMapping
import io.vamp.core.container_driver.notification.{ContainerDriverNotificationProvider, UnsupportedDeployableSchema}
import io.vamp.core.model.artifact._
import io.vamp.core.model.resolver.DeploymentTraitResolver
import org.json4s.{DefaultFormats, Extraction, Formats}

import scala.concurrent.ExecutionContext

abstract class AbstractContainerDriver(ec: ExecutionContext) extends ContainerDriver with DeploymentTraitResolver with ContainerDriverNotificationProvider {
  protected implicit val executionContext = ec

  protected val nameDelimiter = "/"
  protected val idMatcher = """^(([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])\\.)*([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])$""".r

  protected def appId(deployment: Deployment, breed: Breed): String = s"$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  protected def processable(id: String): Boolean = id.split(nameDelimiter).size == 3

  protected def nameMatcher(id: String): (Deployment, Breed) => Boolean = { (deployment: Deployment, breed: Breed) => id == appId(deployment, breed) }

  protected def artifactName2Id(artifact: Artifact) = artifact.name match {
    case idMatcher(_*) => artifact.name
    case _ => Hash.hexSha1(artifact.name).substring(0, 20)
  }

  protected def portMappings(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): List[DockerPortMapping] = {
    service.breed.ports.map(port =>
      port.value match {
        case Some(_) => DockerPortMapping(port.number)
        case None => DockerPortMapping(deployment.ports.find(p => TraitReference(cluster.name, TraitReference.Ports, port.name).toString == p.name).get.number)
      })
  }

  protected def environment(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Map[String, String] =
    service.breed.environmentVariables.map({ ev =>
      val name = ev.alias.getOrElse(ev.name)
      val value = deployment.environmentVariables.find(e => TraitReference(cluster.name, TraitReference.EnvironmentVariables, ev.name).toString == e.name).get.interpolated.get
      name -> value
    }).toMap

  protected def validateSchemaSupport(schema: String, enum: Enumeration) = {
    if (!enum.values.exists(en => en.toString.compareToIgnoreCase(schema) == 0))
      throwException(UnsupportedDeployableSchema(schema, enum.values.map(_.toString.toLowerCase).mkString(", ")))
  }

  protected def mergeWithDialect(deployment: Deployment, cluster: DeploymentCluster, app: Any, dialect: Any)(implicit formats: Formats = DefaultFormats) = {
    Extraction.decompose(interpolate(deployment, cluster, dialect)) merge Extraction.decompose(app)
  }

  private def interpolate(deployment: Deployment, cluster: DeploymentCluster, dialect: Any) = {
    def visit(any: Any): Any = any match {
      case value: String => resolve(value, valueFor(deployment, cluster))
      case map: scala.collection.Map[_, _] => map.map {
        case (key, value) => key -> visit(value)
      }
      case _ => any
    }

    visit(dialect)
  }
}
