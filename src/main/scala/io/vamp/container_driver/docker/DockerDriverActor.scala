package io.vamp.container_driver.docker

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.messages.{ Container ⇒ SpotifyContainer, ContainerInfo ⇒ _, _ }
import com.typesafe.config.ConfigFactory
import io.vamp.common.crypto.Hash
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.ContainerDriverActor._
import io.vamp.container_driver.DockerAppDriver.{ DeployDockerApp, RetrieveDockerApp, UndeployDockerApp }
import io.vamp.container_driver._
import io.vamp.container_driver.notification.UnsupportedContainerDriverRequest
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Quantity }

import scala.collection.JavaConverters._
import scala.collection.mutable.{ Map ⇒ MutableMap }
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

object DockerDriverActor {

  object Schema extends Enumeration {
    val Docker = Value
  }

}

class DockerDriverActor extends ContainerDriverActor with ContainerDriver {

  private val configuration = ConfigFactory.load().getConfig("vamp.container-driver.docker")

  protected val nameDelimiter = "_"
  protected val idMatcher = """^(([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])\\.)*([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])$""".r

  private val docker = {

    val serverAddress = configuration.getString("repository.server-address")

    if (serverAddress.nonEmpty) {

      val email = configuration.getString("repository.email")
      val username = configuration.getString("repository.username")
      val password = configuration.getString("repository.password")

      val authConfig = AuthConfig.builder().email(email).username(username).password(password).serverAddress(serverAddress).build()
      DefaultDockerClient.fromEnv().authConfig(authConfig).build()

    } else DefaultDockerClient.fromEnv().build()
  }

  private val vampLabel = "deployment-service"

  private val vampWorkflowLabel = "workflow"

  def receive = {
    case InfoRequest                ⇒ reply(info)
    case Get(services)              ⇒ get(services)
    case d: Deploy                  ⇒ reply(Future(deploy(d.deployment, d.cluster, d.service, d.update)))
    case u: Undeploy                ⇒ reply(Future(undeploy(u.deployment, u.service)))
    case DeployedGateways(gateways) ⇒ reply(deployedGateways(gateways))
    case d: DeployDockerApp         ⇒ reply(Future.successful(deploy(d.app, d.update)))
    case u: UndeployDockerApp       ⇒ reply(Future.successful(undeploy(u.app)))
    case r: RetrieveDockerApp       ⇒ reply(Future.successful(retrieve(r.app)))
    case any                        ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }

  override def postStop() = docker.close()

  override protected def appId(deployment: Deployment, breed: Breed): String = s"vamp$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  override protected def artifactName2Id(artifact: Artifact): String = artifact.name match {
    case idMatcher(_*) ⇒ artifact.name
    case _             ⇒ Hash.hexSha1(artifact.name).substring(0, 20)
  }

  private def info: Future[ContainerInfo] = Future(docker.info()).map {
    case info ⇒ ContainerInfo("docker", info)
  }

  private def get(deploymentServices: List[DeploymentServices]) = {

    import DefaultScaleProtocol.DefaultScaleFormat
    import spray.json._

    log.debug(s"docker get all")

    val replyTo = sender()

    Future(docker.listContainers().asScala).map { containers ⇒

      val deployed = containers.flatMap(container ⇒ id(container, vampLabel).map(_ -> container)).toMap

      deploymentServices.flatMap(ds ⇒ ds.services.map((ds.deployment, _))).map {
        case (deployment, service) ⇒

          deployed.get(appId(deployment, service.breed)) match {

            case Some(container) if processable(container, vampLabel) ⇒

              val scale = container.labels().get("scale").parseJson.convertTo[DefaultScale]

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

  private def id(container: SpotifyContainer, label: String): Option[String] = {
    if (container.labels().getOrDefault("vamp", "") == label) Option(container.labels().get("id")) else None
  }

  private def processable(container: SpotifyContainer, label: String) = {
    container.labels().getOrDefault("vamp", "") == label && container.status().startsWith("Up") && container.labels().containsKey("scale")
  }

  private def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) = {

    def run() = {
      service.breed.deployable match {
        case Deployable(schema, Some(definition)) if DockerDriverActor.Schema.Docker.toString.compareToIgnoreCase(schema) == 0 ⇒
          val config = containerConfiguration(deployment, cluster, service, docker(deployment, cluster, service, definition))
          val container = docker.createContainer(config)
          docker.startContainer(container.id())
        case _ ⇒ None
      }
    }

    val id = appId(deployment, service.breed)
    val image = service.breed.deployable.definition.get

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

  private def containerConfiguration(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, dockerDefinition: Docker): ContainerConfig = {

    import DefaultScaleProtocol.DefaultScaleFormat
    import spray.json._

    val hostConfig = HostConfig.builder()
    val spotifyContainer = ContainerConfig.builder()

    hostConfig.privileged(dockerDefinition.privileged)
    dockerDefinition.parameters.find(_.key == "security-opt").foreach { security ⇒ hostConfig.securityOpt(security.value) }

    val docker = Docker(dockerDefinition.image, dockerDefinition.portMappings, dockerDefinition.parameters)

    spotifyContainer.image(docker.image)
    val portBindings = new java.util.HashMap[String, java.util.List[PortBinding]]()

    docker.portMappings.map(port ⇒ {
      val hostPorts = new java.util.ArrayList[PortBinding]()
      hostPorts.add(PortBinding.of("0.0.0.0", port.containerPort))
      portBindings.put(port.containerPort.toString, hostPorts)
    })

    val labels: MutableMap[String, String] = MutableMap()
    labels += ("vamp" -> vampLabel)
    labels += ("deployment" -> deployment.name)
    labels += ("cluster" -> cluster.name)
    labels += ("service" -> service.breed.name)
    labels += ("id" -> appId(deployment, service.breed))

    if (service.scale.isDefined)
      labels += ("scale" -> service.scale.get.toJson.toString)

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
          hostConfig.portBindings(portBindings).networkMode("bridge")
      }
    } else {
      hostConfig.portBindings(portBindings).networkMode("bridge")
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

  private def deploy(app: DockerApp, update: Boolean) = if (app.container.isDefined) {

    def run() = {
      val container = docker.createContainer(containerConfiguration(app))
      docker.startContainer(container.id())
    }

    val image = app.container.get.image

    if (Try(docker.inspectImage(image)).isFailure) {
      log.info(s"docker pull image: $image")
      docker.pull(image)
    }

    val exists = retrieve(app.id).isDefined

    if (!exists && !update) {
      log.info(s"docker create app: ${app.id}")
      run()
    } else if (exists && update) {
      log.info(s"docker update app: ${app.id}")
      undeploy(app.id)
      run()
    }
  }

  private def undeploy(appId: String) = retrieve(appId) match {
    case Some(container) ⇒
      log.info(s"docker delete app: $appId")
      docker.killContainer(container.id())
      docker.removeContainer(container.id())
    case _ ⇒
  }

  private def retrieve(appId: String): Option[SpotifyContainer] = {
    docker.listContainers().asScala.find(container ⇒ id(container, vampWorkflowLabel).contains(appId))
  }

  private def containerConfiguration(app: DockerApp): ContainerConfig = {

    import DefaultScaleProtocol.DefaultScaleFormat
    import spray.json._

    val hostConfig = HostConfig.builder()
    val spotifyContainer = ContainerConfig.builder()

    val dockerDefinition = app.container.get
    hostConfig.privileged(dockerDefinition.privileged)
    dockerDefinition.parameters.find(_.key == "security-opt").foreach { security ⇒ hostConfig.securityOpt(security.value) }

    val docker = Docker(dockerDefinition.image, dockerDefinition.portMappings, dockerDefinition.parameters)

    spotifyContainer.image(docker.image)
    val portBindings = new java.util.HashMap[String, java.util.List[PortBinding]]()

    docker.portMappings.map(port ⇒ {
      val hostPorts = new java.util.ArrayList[PortBinding]()
      hostPorts.add(PortBinding.of("0.0.0.0", port.containerPort))
      portBindings.put(port.containerPort.toString, hostPorts)
    })

    val labels: MutableMap[String, String] = MutableMap()
    labels += ("vamp" -> vampWorkflowLabel)
    labels += ("id" -> app.id)
    labels += ("scale" -> DefaultScale("", Quantity(app.cpu), MegaByte(app.memory), app.instances).toJson.toString)

    hostConfig.portBindings(portBindings).networkMode("bridge")

    val env = app.environmentVariables.map {
      case (key, value) ⇒ s"$key=$value"
    } toList

    spotifyContainer.env(env.asJava)

    spotifyContainer.labels(labels.asJava)
    spotifyContainer.hostConfig(hostConfig.build())

    if (app.command.isDefined) spotifyContainer.entrypoint(app.command.get.split(" ").toList.asJava)

    spotifyContainer.build()
  }
}
