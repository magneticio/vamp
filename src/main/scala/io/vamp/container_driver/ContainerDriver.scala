package io.vamp.container_driver

import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider, UndefinedDockerImage, UnsupportedDeployableType }
import io.vamp.model.artifact._
import io.vamp.model.resolver.DeploymentTraitResolver
import io.vamp.model.workflow.Workflow

trait ContainerDriver extends DeploymentTraitResolver with ContainerDriverNotificationProvider with ExecutionContextProvider {

  protected def nameDelimiter: String

  protected def appId(workflow: Workflow): String

  protected def appId(deployment: Deployment, breed: Breed): String

  protected def artifactName2Id(artifact: Artifact): String

  protected def supportedDeployableTypes: List[DeployableType]

  protected def portMappings(workflow: Workflow): List[DockerPortMapping] = {
    workflow.breed.asInstanceOf[DefaultBreed].ports.collect {
      case port if port.value.isDefined ⇒ DockerPortMapping(port.number)
    }
  }

  protected def portMappings(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): List[DockerPortMapping] = {
    service.breed.ports.map(port ⇒
      port.value match {
        case Some(_) ⇒ DockerPortMapping(port.number)
        case None    ⇒ DockerPortMapping(deployment.ports.find(p ⇒ TraitReference(cluster.name, TraitReference.Ports, port.name).toString == p.name).get.number)
      })
  }

  protected def environment(workflow: Workflow): Map[String, String] = {
    workflow.breed.asInstanceOf[DefaultBreed].environmentVariables.map(ev ⇒ ev.alias.getOrElse(ev.name) -> ev.interpolated.getOrElse("")).toMap
  }

  protected def environment(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Map[String, String] = {
    service.environmentVariables.map(ev ⇒ ev.alias.getOrElse(ev.name) -> ev.interpolated.getOrElse("")).toMap
  }

  protected def validateDeployable(deployable: Deployable) = {
    if (!supportedDeployableTypes.exists(_.matches(deployable)))
      throwException(UnsupportedDeployableType(deployable.`type`, supportedDeployableTypes.map(_.`type`).mkString(", ")))

    if (DockerDeployable.matches(deployable) && deployable.definition.isEmpty)
      throwException(UndefinedDockerImage)
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

  protected def docker(workflow: Workflow): Docker = {
    Docker(workflow.breed.asInstanceOf[DefaultBreed].deployable.definition, portMappings(workflow), Nil, privileged = false)
  }

  protected def docker(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Docker = {
    val (privileged, arguments) = service.arguments.partition(_.privileged)
    val parameters = arguments.map(argument ⇒ DockerParameter(argument.key, argument.value))
    Docker(service.breed.deployable.definition, portMappings(deployment, cluster, service), parameters, privileged.headOption.exists(_.value.toBoolean))
  }

  protected def labels(workflow: Workflow) = {
    Map("workflow" -> workflow.name, "breed" -> workflow.breed.name)
  }

  protected def labels(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    Map("deployment" -> deployment.name, "cluster" -> cluster.name, "service" -> service.breed.name)
  }
}
