package io.vamp.core.container_driver.docker

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import io.vamp.common.crypto.Hash
import io.vamp.core.container_driver.marathon.api._
import io.vamp.core.container_driver.{ContainerDriver, ContainerInfo, ContainerServer, ContainerService}
import io.vamp.core.model.artifact._
import org.slf4j.LoggerFactory
import tugboat.{ContainerConfig, Container, ContainerDetails}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class DockerDriver(ec: ExecutionContext, url: String) extends ContainerDriver {
  protected implicit val executionContext = ec

  private val logger = Logger(LoggerFactory.getLogger(classOf[DockerDriver]))

  private val nameDelimiter = "_"
  private val idMatcher = """^(([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])\\.)*([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])$""".r
  private val dockerMinimumMemory =  4 * 1024 * 1024

  private val defaultCpu =ConfigFactory.load().getDouble("vamp.core.dictionary.default-scale.memory")
  private val defaultMemory =ConfigFactory.load().getDouble("vamp.core.dictionary.default-scale.memory")
  private val defaultInstances =ConfigFactory.load().getInt("vamp.core.dictionary.default-scale.memory")

  private val defaultScale: DefaultScale = DefaultScale(name = "", cpu = defaultCpu, memory = defaultMemory, instances = defaultInstances)

  lazy val timeout = ConfigFactory.load().getInt("vamp.core.container-driver.response-timeout") seconds

  private val docker = tugboat.Docker()

  def info: Future[ContainerInfo] = docker.info().map {
    ContainerInfo("docker", _)
  }

  def all: Future[List[ContainerService]] = Future({
    logger.debug(s"docker get all")

    val details: List[ContainerDetails] =
      for {
        container: Container <- getContainers
        detail: ContainerDetails = getContainerDetails(container.id)
      } yield detail

    for (detail <- details) logger.debug(s"[ALL] id: ${detail.id} name: ${detail.name} [monitored by VAMP: ${processable(detail.name)}]")

    // TODO service of the same deloyment need to be folded into a single containerDetail enitity
    val containerDetails: List[ContainerService] =
      for {
        detail <- details if processable(detail.name)
      } yield containerService(detail)
    logger.debug("[ALL]: " + containerDetails.toString())

    containerDetails
  })

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) = {
    val id = appId(deployment, service.breed)
    findContainer(id) match {
      case None =>
        logger.debug(s"[DEPLOY] Container $id does not exist, needs creating")
        createContainer(id, deployment, cluster, service)
      case Some(found) if update =>
        logger.debug(s"[DEPLOY] Container $id already exists, needs updating")
        updateContainer(id, deployment, cluster, service)
      case Some(found) =>
        logger.warn(s"[DEPLOY] Container $id already exists")
        Future(None)
    }
  }

  def undeploy(deployment: Deployment, service: DeploymentService) = {
    val id = appId(deployment, service.breed)
    logger.info(s"docker delete app: $id")
    Future(
      findContainer(id) match {
        case Some(found) =>
          logger.debug(s"[UNDEPLOY] Container $id found, trying to kill it")
          docker.containers.get(found.id).kill()
        case None =>
          logger.debug(s"[UNDEPLOY] Container $id does not exist")
      }
    )
  }

  private def getContainers: List[Container] = {
    val dockerContainers: Future[List[Container]] = docker.containers.list()
    Await.result(dockerContainers, timeout)
  }

  private def getContainerDetails(id: String): ContainerDetails = {
    val containerDetails: Future[ContainerDetails] = docker.containers.get(id)()
    Await.result(containerDetails, timeout)
  }



  private def containerService(container: ContainerDetails): ContainerService =
    ContainerService(
      matching = nameMatcher(container.name),
      scale = DefaultScale(name = "", cpu = container.config.cpuShares, memory = defaultMemory, instances = 1 /* TODO memory locked, instances set to 1 */),
      servers = List(
        ContainerServer(
          id = serverNameFromContainer(container),
          host = container.config.hostname,
          ports = container.config.exposedPorts.map(port => port.toInt).toList,
          deployed = container.state.running)
      )
    )

  private def serverNameFromContainer(container: ContainerDetails): String = {
    val parts = container.name.split("_")
    if (parts.size == 2)
      parts(1)
    else
      container.name
  }

  private def deploymentNameFromContainer(container: ContainerDetails): String = {
    val parts = container.name.split("_")
    if (parts.size == 2)
      parts(0).substring(1)
    else
      container.name
  }

  private def findContainer(name: String): Option[Container] = {
    val matching = getContainers.filter(container => getContainerDetails(container.id).name == name)
    if (matching.isEmpty) None
    else Some(matching.head)
  }



  private def createContainer(id: String, deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    //TODO check if the docker image is already present, or needs to be pulled first

    val containerwithName = docker.containers.create(service.breed.deployable.name).name(id)

    val containerPrep = service.scale match {
      case Some(scale) => containerwithName.cpuShares(scale.cpu.toInt).memory(if(scale.memory.toLong < dockerMinimumMemory) dockerMinimumMemory else scale.memory.toLong  )
      case None => containerwithName
    }

    // TODO environment variables need to be set

    // Create the actual container
    val container = Await.result(containerPrep(), timeout)

    logger.debug(s"[DEPLOY] ports: ${portMappings(deployment, cluster, service)}")

    // Configure the container for starting
    var prepStart = docker.containers.get(container.id).start
    for (port <- portMappings(deployment, cluster, service)) {
      prepStart = prepStart.portBind(tugboat.Port.Tcp(port.containerPort), tugboat.PortBinding.local(port.hostPort))
    }
    Future(prepStart())
  }

  private def updateContainer(id: String, deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    // TODO implement this functionality
    logger.debug(s"[DEPLOY] update ports: ${portMappings(deployment, cluster, service)}")
    Future(logger.debug("Implement this method"))
  }


  private def portMappings(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): List[CreatePortMapping] = {
    service.breed.ports.map({ port =>
      port.direction match {
        case Trait.Direction.Out => CreatePortMapping(port.value.get)
        case Trait.Direction.In =>
          CreatePortMapping(deployment.environmentVariables.find({
            case (Trait.Name(Some(scope), Some(Trait.Name.Group.Ports), value), _) if scope == cluster.name && value == port.name.value => true
            case _ => false
          }).get._2.toString.toInt)
      }
    })
  }

  private def environment(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Map[String, String] = {
    def matchParameter(ev: EnvironmentVariable, parameter: Trait.Name): Boolean = {
      ev.name match {
        case Trait.Name(None, None, value) =>
          parameter.scope.isDefined && parameter.scope.get == cluster.name && parameter.group == Some(Trait.Name.Group.EnvironmentVariables) && parameter.value == value

        case Trait.Name(Some(scope), group, value) =>
          parameter.scope == service.dependencies.get(scope) && parameter.group == group && parameter.value == value

        case _ => false
      }
    }
    service.breed.environmentVariables.filter(_.direction == Trait.Direction.In).flatMap({ ev =>
      deployment.environmentVariables.find({ case (name, value) => matchParameter(ev, name) }) match {
        case Some((name, value)) =>
          (ev.alias match {
            case None => ev.name.value
            case Some(alias) => alias
          }) -> value.toString :: Nil
        case _ => Nil
      }
    }).toMap
  }


  private def appId(deployment: Deployment, breed: Breed): String = s"/${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  private def processable(id: String): Boolean = id.split(nameDelimiter).size == 2

  private def nameMatcher(id: String): (Deployment, Breed) => Boolean = { (deployment: Deployment, breed: Breed) => id == appId(deployment, breed) }

  private def artifactName2Id(artifact: Artifact) = artifact.name match {
    case idMatcher(_*) => artifact.name
    case _ => Hash.hexSha1(artifact.name).substring(0, 20)
  }
}

