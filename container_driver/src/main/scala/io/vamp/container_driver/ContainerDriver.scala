package io.vamp.container_driver

import com.typesafe.scalalogging.LazyLogging
import io.vamp.common._
import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.common.notification.NotificationProvider
import io.vamp.container_driver.notification.{ContainerDriverNotificationProvider, UndefinedDockerImage, UnsupportedDeployableType}
import io.vamp.model.artifact._
import io.vamp.model.resolver.{DeploymentValueResolver, WorkflowValueResolver}

object ContainerDriver {

  val labelNamespace: ConfigMagnet[String] = Config.string("vamp.container-driver.label-namespace")

  def withNamespace(label: String)(implicit namespace: Namespace): String = {
    val ns = labelNamespace()
    if (ns.isEmpty) label else s"$ns.$label"
  }
}

trait ContainerDriver extends ContainerDriverMapping with ContainerDriverValidation with ContainerDriverNotificationProvider with NamespaceProvider with ExecutionContextProvider{

  protected def appId(workflow: Workflow): String

  protected def appId(deployment: Deployment, breed: Breed): String

  protected def artifactName2Id(artifact: Artifact): String
}

trait ContainerDriverMapping extends DeploymentValueResolver with WorkflowValueResolver with LazyLogging{
  this: NotificationProvider with NamespaceProvider with ExecutionContextProvider ⇒

  protected def portMappings(workflow: Workflow, network: String): List[DockerPortMapping] =
    workflow.breed.asInstanceOf[DefaultBreed].ports.collect {
      case port if port.value.isDefined ⇒
        network match {
          case "USER" ⇒ DockerPortMapping(port.number, None)
          case _      ⇒ DockerPortMapping(port.number, Some(0))
        }

    }

  protected def portMappings(
    deployment: Deployment,
    cluster:    DeploymentCluster,
    service:    DeploymentService,
    network:    String
  ): List[DockerPortMapping] =
    service.breed.ports.map { port ⇒
      val hostPort = network match {
        case "USER" ⇒ None
        case _      ⇒ Some(0)
      }

      port.value match {
        case Some(_) ⇒ DockerPortMapping(port.number, hostPort)
        case None ⇒
          val containerPort = deployment
            .ports
            .find(p ⇒ TraitReference(cluster.name, TraitReference.Ports, port.name).toString == p.name)
            .get
            .number

          DockerPortMapping(containerPort, hostPort)
      }
    }

  protected def environment(workflow: Workflow): Map[String, String] =
    workflow
      .breed
      .asInstanceOf[DefaultBreed]
      .environmentVariables
      .map(ev ⇒ ev.alias.getOrElse(ev.name) → ev.interpolated.getOrElse(""))
      .toMap

  protected def environment(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Map[String, String] =
    service
      .environmentVariables
      .map(ev ⇒ ev.alias.getOrElse(ev.name) → ev.interpolated.getOrElse(""))
      .toMap

  protected def interpolate[T](workflow: Workflow, dialect: T): T = {
    def visit(any: Any): Any = any match {
      case value: String ⇒ resolve(value, valueFor(workflow))
      case list: List[_] ⇒ list.map(visit)
      case map: Map[_, _] ⇒ map.map {
        case (key, value) ⇒ key → visit(value)
      }
      case _ ⇒ any
    }

    visit(dialect).asInstanceOf[T]
  }

  protected def interpolate[T](deployment: Deployment, service: Option[DeploymentService], dialect: T): T = {
    def visit(any: Any): Any = any match {
      case value: String ⇒ resolve(value, valueFor(deployment, service))
      case list: List[_] ⇒ list.map(visit)
      case map: Map[_, _] ⇒ map.map {
        case (key, value) ⇒ key → visit(value)
      }
      case _ ⇒ any
    }

    visit(dialect).asInstanceOf[T]
  }

  protected def docker(workflow: Workflow): Docker = {

    val (privileged, parameters) = privilegedAndParameters(workflow.arguments)

    val network = workflow.network.getOrElse(Docker.network())

    Docker(
      image = workflow.breed.asInstanceOf[DefaultBreed].deployable.definition,
      portMappings = portMappings(workflow, network),
      parameters = parameters,
      privileged = privileged,
      network = network
    )
  }

  protected def docker(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Docker = {

    val (privileged, parameters) = privilegedAndParameters(service.arguments)

    logger.info("ContainerDriver service.network {} cluster.network {}, Docker.network {}", service.network.getOrElse("Empty"),cluster.network.getOrElse("Empty"), Docker.network())

    val network = service.network.orElse(cluster.network).getOrElse(Docker.network())

    Docker(
      image = service.breed.deployable.definition,
      portMappings = portMappings(deployment, cluster, service, network),
      parameters = parameters,
      privileged = privileged,
      network = network
    )
  }

  private def privilegedAndParameters(arguments: List[Argument]): (Boolean, List[DockerParameter]) = {
    val (privileged, args) = arguments.partition(_.privileged)
    val parameters = args.map(argument ⇒ DockerParameter(argument.key, argument.value))
    (privileged.headOption.exists(_.value.toBoolean), parameters)
  }

  protected def labels(workflow: Workflow) = Map(
    ContainerDriver.withNamespace("workflow") → workflow.name,
    ContainerDriver.withNamespace("breed") → workflow.breed.name
  )

  protected def labels(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = Map(
    ContainerDriver.withNamespace("deployment") → deployment.name,
    ContainerDriver.withNamespace("cluster") → cluster.name,
    ContainerDriver.withNamespace("service") → service.breed.name
  )
}

trait ContainerDriverValidation {
  this: NamespaceProvider with NotificationProvider ⇒

  protected def supportedDeployableTypes: List[DeployableType]

  protected def validateDeployable(deployable: Deployable): Unit = {
    if (!supportedDeployableTypes.exists(_.matches(deployable)))
      throwException(UnsupportedDeployableType(deployable.defaultType(), supportedDeployableTypes.flatMap(_.types).mkString(", ")))

    if (DockerDeployableType.matches(deployable) && deployable.definition.isEmpty)
      throwException(UndefinedDockerImage)
  }

}
