package io.vamp.container_driver

import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider, UnsupportedDeployableSchema }
import io.vamp.model.artifact._
import io.vamp.model.resolver.DeploymentTraitResolver

trait ContainerDriver extends DeploymentTraitResolver with ContainerDriverNotificationProvider with ExecutionContextProvider {

  protected def nameDelimiter: String

  protected def appId(deployment: Deployment, breed: Breed): String

  protected def artifactName2Id(artifact: Artifact): String

  protected def portMappings(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): List[DockerPortMapping] = {
    service.breed.ports.map(port ⇒
      port.value match {
        case Some(_) ⇒ DockerPortMapping(port.number)
        case None    ⇒ DockerPortMapping(deployment.ports.find(p ⇒ TraitReference(cluster.name, TraitReference.Ports, port.name).toString == p.name).get.number)
      })
  }

  protected def environment(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Map[String, String] = {
    service.environmentVariables.map(ev ⇒ ev.alias.getOrElse(ev.name) -> ev.interpolated.getOrElse("")).toMap
  }

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

  protected def docker(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, definition: String): Docker = {
    val (privileged, arguments) = service.arguments.partition(_.privileged)
    val parameters = arguments.map(argument ⇒ DockerParameter(argument.key, argument.value))
    Docker(definition, portMappings(deployment, cluster, service), parameters, privileged.headOption.exists(_.value.toBoolean))
  }

  protected def labels(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    Map("deployment" -> deployment.name, "cluster" -> cluster.name, "service" -> service.breed.name)
  }
}
