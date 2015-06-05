package io.vamp.core.container_driver.docker

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import io.vamp.core.container_driver._
import io.vamp.core.container_driver.marathon.api.CreatePortMapping
import io.vamp.core.model.artifact._
import org.slf4j.LoggerFactory
import tugboat.Create.Response
import tugboat._

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class DockerDriver(ec: ExecutionContext) extends AbstractContainerDriver(ec) with DummyScales with ContainerCache {

  override protected val nameDelimiter = "_"

  override protected def appId(deployment: Deployment, breed: Breed): String = s"/vamp$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  private val dockerMinimumMemory = 4 * 1024 * 1024

  private val logger = Logger(LoggerFactory.getLogger(classOf[DockerDriver]))

  private val docker = tugboat.Docker()

  private val defaultHost = ConfigFactory.load().getString("vamp.core.router-driver.host")

  def info: Future[ContainerInfo] = docker.info().map {
    logger.debug(s"docker get info :$docker")
    ContainerInfo("docker", _)
  }

  def all: Future[List[ContainerService]] = async {
    logger.debug(s"docker get all")

    // Get all containers & container details
    val details: List[Future[ContainerDetails]] =
      for {
        container: Container <- await(docker.containers.list())
        detail: Future[ContainerDetails] = getContainerDetails(container.id)
      } yield detail

    // Log which container have been found
    for (detail <- details) logContainerDetails(detail)

    val actualDetails: List[ContainerDetails] = await(Future.sequence(details))
    val containerDetails: List[ContainerService] = details2Services(for {detail <- actualDetails if processable(detail.name)} yield detail)
    logger.debug("[ALL]: " + containerDetails.toString())
    containerDetails
  }

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) = {
    val containerName = appId(deployment, service.breed)
    findContainerIdInCache(containerName) match {
      case None =>
        logger.info(s"[DEPLOY] Container $containerName does not exist, needs creating")
        createAndStartContainer(containerName, deployment, cluster, service)
      case Some(found) if update =>
        logger.info(s"[DEPLOY] Container $containerName already exists, needs updating")
        addScale(Future(found), service.scale)
        Future(None)
      case Some(found) =>
        logger.warn(s"[DEPLOY] Container $containerName already exists, no action")
        Future(None)
    }
  }

  def undeploy(deployment: Deployment, service: DeploymentService) = {
    val containerName = appId(deployment, service.breed)
    logger.info(s"docker delete app: $containerName")
    Future(
      findContainerIdInCache(containerName) match {
        case Some(found) =>
          logger.debug(s"[UNDEPLOY] Container $containerName found, trying to kill it")
          removeScale(containerName)
          val container = docker.containers.get(found)
          container.kill()
          container.delete()
          removeContainerIdFromCache(found)
        case None =>
          logger.debug(s"[UNDEPLOY] Container $containerName does not exist")
      }
    )
  }

  private def details2Services(details: List[ContainerDetails]): List[ContainerService] = {
    val serviceMap: Map[String, List[ContainerDetails]] = details.groupBy(x => serverNameFromContainer(x))
    serviceMap.map({ deployment =>
      val details = deployment._2.head
      val scale = getScale(details.id)
      val server = detail2Server(details)

      ContainerService(
        matching = nameMatcher(details.name),
        scale = scale,
        servers = (0 until scale.instances).map(_ => server).toList)
    }).toList
  }

  private def detail2Server(cd: ContainerDetails): ContainerServer = {
    logger.trace(s"Details2Server containerDetails: $cd" )
    ContainerServer(
      name = serverNameFromContainer(cd),
      host = if (cd.config.hostname.isEmpty) defaultHost else cd.config.hostname,
      ports = cd.networkSettings.ports.flatMap(port => port._2.map(e => e.hostPort)).toList,
      deployed = cd.state.running
    )
  }

  private def serverNameFromContainer(container: ContainerDetails): String = {
    val parts = container.name.split(nameDelimiter)
    if (parts.size == 3)
      parts(2)
    else
      container.name
  }

  /**
   * Creates and starts a container
   * If the image is not available, it will be pulled first
   */
  private def createAndStartContainer(containerName: String, deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Future[_] = async {
    val dockerImageName = service.breed.deployable.name
    val allImages : List[Image] = await(docker.images.list())
    val taggedImages =  allImages.filter(image => image.repoTags.contains(dockerImageName))

    if (taggedImages.isEmpty) {
      pullImage(dockerImageName)
    }

    val response = createDockerContainer(containerName, dockerImageName, service.scale, environment(deployment, cluster, service), portMappings(deployment, cluster, service))

    addContainerToCache(containerName, getContainerFromResponseId(response))
    addScale(getContainerFromResponseId(response), service.scale)

    startDockerContainer(getContainerFromResponseId(response), portMappings(deployment, cluster, service))
  }

  /**
   * Pull images from the repo
   */
  private def pullImage(name: String): Unit = {
    docker.images.pull(name).stream {
      case Pull.Status(msg) => logger.debug(s"[DEPLOY] pulling image $name: $msg")
      case Pull.Progress(msg, _, details) =>
        logger.debug(s"[DEPLOY] pulling image $name: $msg")
        details.foreach { dets =>
          logger.debug(s"[DEPLOY] pulling image $name: ${dets.bar}")
        }
      case Pull.Error(msg, _) => logger.error(s"[DEPLOY] pulling image $name failed: $msg")
    }
  }


  /**
   * Create a docker container (without starting it)
   * Tries to set the cpu shares & memory based on the supplied scale
   */
  private def createDockerContainer(containerName: String, dockerImageName: String, serviceScale: Option[DefaultScale], env: Map[String, String], ports: List[CreatePortMapping]): Future[Response] = async {
    val containerWithName = docker.containers.create(dockerImageName).name(containerName)

    var containerPrep = serviceScale match {
      case Some(scale) => containerWithName.cpuShares(scale.cpu.toInt).memory(if (scale.memory.toLong < dockerMinimumMemory) dockerMinimumMemory else scale.memory.toLong)
      case None => containerWithName
    }
    for (v <- env) {
      logger.trace(s"[CreateDockerContainer] setting env ${v._1} = ${v._2}")
      containerPrep = containerPrep.env(v)
    }
    for (p <- ports) {
      logger.debug(s"[CreateDockerContainer] exposed ports ${p.containerPort}")
      containerPrep = containerPrep.exposedPorts(p.containerPort.toString)
    }

    await(containerPrep())
  }

  /**
   * Start the container
   */
  private def startDockerContainer(id: Future[String], ports: List[CreatePortMapping]): Future[_] = async {
    // Configure the container for starting
    var startPrep  = docker.containers.get(await(id)).start
    for (port <- ports) {
      logger.debug(s"[StartContainer] setting port: 0.0.0.0:${port.hostPort} -> ${port.containerPort}/tcp")
      startPrep = startPrep.portBind(tugboat.Port.Tcp(port.containerPort), tugboat.PortBinding.local(port.hostPort))
    }
    await(startPrep())
  }

  /**
   * Get details of a single container
   */
  private def getContainerDetails(id: String): Future[ContainerDetails] = async {
    await(docker.containers.get(id)())
  }

  /**
   * Get the containerId from a response
   */
  private def getContainerFromResponseId(response: Future[Response]): Future[String] = async {
    await(response).id
  }

  /**
   * Creates a nice debug log message
   */
  private def logContainerDetails(detail: Future[ContainerDetails]) = async {
    logger.debug(s"[ALL] name: ${await(detail).name} ${
      if (processable(await(detail).name)) {
        "[Monitored by VAMP]"
      } else {
        "[Non-vamp container]"
      }
    }")
  }

}

