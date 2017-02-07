package io.vamp.container_driver.docker

import akka.actor.ActorRef
import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.{ Container ⇒ SpotifyContainer, ContainerInfo ⇒ _, _ }
import io.vamp.common.config.Config
import io.vamp.common.spi.ClassMapper
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.ContainerDriverActor._
import io.vamp.container_driver._
import io.vamp.container_driver.notification.UnsupportedContainerDriverRequest
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Quantity }
import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods._
import org.json4s.native.Serialization._

import scala.collection.JavaConverters._
import scala.collection.mutable.{ Map ⇒ MutableMap }
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

class DockerDriverActorMapper extends ClassMapper {
  val name = "docker"
  val clazz = classOf[DockerDriverActor]
}

class DockerDriverActor extends ContainerDriverActor with ContainerDriver with DockerNameMatcher {

  private val config = "vamp.container-driver.docker"

  protected val nameDelimiter = "_"

  protected val workflowNamePrefix = Config.string(s"$config.workflow-name-prefix")

  private val docker = {

    val serverAddress = Config.string(s"$config.repository.server-address")()

    if (serverAddress.nonEmpty) {

      val email = Config.string(s"$config.repository.email")()
      val username = Config.string(s"$config.repository.username")()
      val password = Config.string(s"$config.repository.password")()

      val authConfig = AuthConfig.builder().email(email).username(username).password(password).serverAddress(serverAddress).build()
      DefaultDockerClient.fromEnv().authConfig(authConfig).build()

    } else DefaultDockerClient.fromEnv().build()
  }

  private val vampLabel = "deployment-service"

  private val vampWorkflowLabel = "workflow"

  private implicit val formats = DefaultFormats

  def receive = {

    case InfoRequest                    ⇒ reply(info)

    case Get(services)                  ⇒ get(services)
    case d: Deploy                      ⇒ reply(Future(deploy(d.deployment, d.cluster, d.service, d.update)))
    case u: Undeploy                    ⇒ reply(Future(undeploy(u.deployment, u.service)))
    case DeployedGateways(gateways)     ⇒ reply(deployedGateways(gateways))

    case GetWorkflow(workflow, replyTo) ⇒ get(workflow, replyTo)
    case d: DeployWorkflow              ⇒ reply(Future.successful(deploy(d.workflow, d.update)))
    case u: UndeployWorkflow            ⇒ reply(Future.successful(undeploy(u.workflow)))

    case any                            ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }

  override def postStop() = docker.close()

  override protected def appId(workflow: Workflow): String = s"$workflowNamePrefix$nameDelimiter${artifactName2Id(workflow)}"

  override protected def appId(deployment: Deployment, breed: Breed): String = s"vamp$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  override protected def supportedDeployableTypes = DockerDeployableType :: Nil

  private def info: Future[ContainerInfo] = Future(docker.info()).map(info ⇒ ContainerInfo("docker", info))

  private def get(deploymentServices: List[DeploymentServices]) = {
    log.debug(s"docker get all")
    val replyTo = sender()

    Future(docker.listContainers().asScala).map { containers ⇒

      val deployed = containers.flatMap(container ⇒ id(container, vampLabel).map(_ → container)).toMap

      deploymentServices.flatMap(ds ⇒ ds.services.map((ds.deployment, _))).map {
        case (deployment, service) ⇒

          deployed.get(appId(deployment, service.breed)) match {

            case Some(container) if processable(container, vampLabel) ⇒

              val scale = parse(container.labels().get(ContainerDriver.withNamespace("scale")), useBigDecimalForDouble = true).extract[DockerServiceScale].toScale

              val host = container.networkSettings().networks().asScala.values.headOption.map {
                attachedNetwork ⇒ attachedNetwork.ipAddress()
              } getOrElse gatewayServiceIp

              val containerPorts = container.ports().asScala.toList
              val ports = service.breed.ports.filter(port ⇒ containerPorts.exists(_.getPrivatePort == port.number)).map(_.number)

              val instances = (1 to scale.instances).map { index ⇒
                ContainerInstance(s"${container.id()}_$index", host, ports, deployed = true)
              } toList

              ContainerService(deployment, service, Option(Containers(scale, instances)))

            case None ⇒ ContainerService(deployment, service, None)
          }
      } foreach { cs ⇒ replyTo ! cs }
    }
  }

  private def get(workflow: Workflow, replyTo: ActorRef) = {
    log.debug(s"docker get workflow")

    Future(docker.listContainers().asScala).map { containers ⇒

      val deployed = containers.flatMap(container ⇒ id(container, vampWorkflowLabel).map(_ → container)).toMap

      val cw = deployed.get(appId(workflow)) match {

        case Some(container) if processable(container, vampWorkflowLabel) ⇒

          val scale = parse(container.labels().get(ContainerDriver.withNamespace("scale")), useBigDecimalForDouble = true).extract[DockerServiceScale].toScale

          val host = container.networkSettings().networks().asScala.values.headOption.map {
            attachedNetwork ⇒ attachedNetwork.ipAddress()
          } getOrElse gatewayServiceIp

          val containerPorts = container.ports().asScala.toList
          val ports = workflow.breed.asInstanceOf[DefaultBreed].ports.filter(port ⇒ containerPorts.exists(_.getPrivatePort == port.number)).map(_.number)

          val instances = (1 to scale.instances).map { index ⇒
            ContainerInstance(s"${container.id()}_$index", host, ports, deployed = true)
          } toList

          ContainerWorkflow(workflow, Option(Containers(scale, instances)))

        case None ⇒ ContainerWorkflow(workflow, None)
      }

      replyTo ! cw
    }
  }

  private def id(container: SpotifyContainer, label: String): Option[String] = {
    if (container.labels().getOrDefault(ContainerDriver.namespace(), "") == label) Option(container.labels().get(ContainerDriver.withNamespace("id"))) else None
  }

  private def processable(container: SpotifyContainer, label: String) = {
    container.labels().getOrDefault(ContainerDriver.namespace(), "") == label && container.status().startsWith("Up") && container.labels().containsKey(ContainerDriver.withNamespace("scale"))
  }

  private def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) = {

    validateDeployable(service.breed.deployable)

    def run() = {
      val config = containerConfiguration(deployment, cluster, service, docker(deployment, cluster, service))
      val container = docker.createContainer(config)
      docker.startContainer(container.id())
    }

    val id = appId(deployment, service.breed)
    val image = service.breed.deployable.definition

    if (Try(docker.inspectImage(image)).isFailure) {
      log.info(s"docker pull image: $image")
      docker.pull(image)
    } else {

      val exists = find(deployment, service).isDefined

      if (!exists && !update) {
        log.info(s"docker create container: $id")
        run()
      } else if (exists && update) {
        log.info(s"docker update container: $id")
        undeploy(deployment, service)
        run()
      }
    }
  }

  private def containerConfiguration(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, docker: Docker): ContainerConfig = {

    val hostConfig = HostConfig.builder()
    val spotifyContainer = ContainerConfig.builder()

    hostConfig.privileged(docker.privileged)
    docker.parameters.find(_.key == "security-opt").foreach { security ⇒ hostConfig.securityOpt(security.value) }

    spotifyContainer.image(docker.image)
    val portBindings = new java.util.HashMap[String, java.util.List[PortBinding]]()

    docker.portMappings.map(port ⇒ {
      val hostPorts = new java.util.ArrayList[PortBinding]()
      hostPorts.add(PortBinding.of("0.0.0.0", port.containerPort))
      portBindings.put(port.containerPort.toString, hostPorts)
    })

    val labels: MutableMap[String, String] = MutableMap()
    labels ++= this.labels(deployment, cluster, service)
    labels += (ContainerDriver.namespace() → vampLabel)
    labels += (ContainerDriver.withNamespace("id") → appId(deployment, service.breed))

    if (service.scale.isDefined)
      labels += (ContainerDriver.withNamespace("scale") → write(DockerServiceScale(service.scale.get)))

    if (service.dialects.contains(Dialect.Docker)) {
      service.dialects.get(Dialect.Docker).map { dialect ⇒
        val values = dialect.asInstanceOf[Map[String, Any]]

        /* Looking for labels */
        val inLabels = values.get("labels").asInstanceOf[Option[Map[String, String]]]
        if (inLabels.isDefined)
          inLabels.get.foreach(f ⇒ {
            labels += f
          })

        /* Getting net parameters */
        val net = values.get("net").asInstanceOf[Option[String]]
        if (net.isDefined)
          hostConfig.portBindings(portBindings).networkMode(net.get)
        else
          hostConfig.portBindings(portBindings).networkMode(docker.network)
      }
    } else {
      hostConfig.portBindings(portBindings).networkMode(docker.network)
    }

    val env = environment(deployment, cluster, service).map {
      case (key, value) ⇒ s"$key=$value"
    } toList

    spotifyContainer.env(env.asJava)

    spotifyContainer.labels(labels.asJava)
    spotifyContainer.hostConfig(hostConfig.build())

    spotifyContainer.build()
  }

  private def undeploy(deployment: Deployment, service: DeploymentService) = find(deployment, service).foreach { container ⇒
    log.info(s"docker delete container: $container.id")
    docker.killContainer(container.id)
    docker.removeContainer(container.id)
  }

  private def find(deployment: Deployment, service: DeploymentService): Option[SpotifyContainer] = {
    val app = appId(deployment, service.breed)
    docker.listContainers().asScala.find(container ⇒ id(container, vampLabel).contains(app))
  }

  private def deploy(workflow: Workflow, update: Boolean) = {

    val breed = workflow.breed.asInstanceOf[DefaultBreed]

    validateDeployable(breed.deployable)

    def run() = {
      val container = docker.createContainer(containerConfiguration(workflow))
      docker.startContainer(container.id())
    }

    val image = breed.deployable.definition

    if (Try(docker.inspectImage(image)).isFailure) {
      log.info(s"docker pull image: $image")
      docker.pull(image)
    }

    val exists = retrieve(workflow).isDefined

    if (!exists && !update) {
      log.info(s"docker create workflow: ${workflow.name}")
      run()
    } else if (exists && update) {
      log.info(s"docker update workflow: ${workflow.name}")
      undeploy(workflow)
      run()
    }
  }

  private def undeploy(workflow: Workflow) = {
    retrieve(workflow) match {
      case Some(container) ⇒
        log.info(s"docker unschedule workflow: ${workflow.name}")
        docker.killContainer(container.id())
        docker.removeContainer(container.id())
      case _ ⇒
    }
  }

  private def retrieve(workflow: Workflow): Option[SpotifyContainer] = {
    docker.listContainers().asScala.find(container ⇒ id(container, vampWorkflowLabel).contains(appId(workflow)))
  }

  private def containerConfiguration(workflow: Workflow): ContainerConfig = {

    val id = appId(workflow)
    val scale = workflow.scale.get.asInstanceOf[DefaultScale]

    val hostConfig = HostConfig.builder()
    val spotifyContainer = ContainerConfig.builder()

    val dockerDefinition = docker(workflow)
    hostConfig.privileged(dockerDefinition.privileged)
    dockerDefinition.parameters.find(_.key == "security-opt").foreach { security ⇒ hostConfig.securityOpt(security.value) }

    spotifyContainer.image(dockerDefinition.image)
    val portBindings = new java.util.HashMap[String, java.util.List[PortBinding]]()

    dockerDefinition.portMappings.map(port ⇒ {
      val hostPorts = new java.util.ArrayList[PortBinding]()
      hostPorts.add(PortBinding.of("0.0.0.0", port.containerPort))
      portBindings.put(port.containerPort.toString, hostPorts)
    })

    val labels: MutableMap[String, String] = MutableMap()
    labels += (ContainerDriver.namespace() → vampWorkflowLabel)
    labels += (ContainerDriver.withNamespace("id") → id)
    labels += (ContainerDriver.withNamespace("scale") → write(DockerServiceScale("", scale.instances, scale.cpu.value, scale.memory.value)))

    hostConfig.portBindings(portBindings).networkMode(dockerDefinition.network)

    val env = environment(workflow).map {
      case (key, value) ⇒ s"$key=$value"
    } toList

    spotifyContainer.env(env.asJava)

    spotifyContainer.labels(labels.asJava)
    spotifyContainer.hostConfig(hostConfig.build())

    spotifyContainer.build()
  }
}

private[docker] object DockerServiceScale {
  def apply(scale: DefaultScale): DockerServiceScale = DockerServiceScale(scale.name, scale.instances, scale.cpu.value, scale.memory.value)
}

private[docker] case class DockerServiceScale(name: String, instances: Int, cpu: Double, memory: Double) {
  val toScale = DefaultScale(name, Map(), Quantity(cpu), MegaByte(memory), instances)
}