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
import scala.concurrent.duration._
import scala.concurrent.{Future, Await, ExecutionContext}
import scala.language.postfixOps

class DockerDriver(ec: ExecutionContext) extends AbstractContainerDriver(ec) with DummyScales {

  override protected val nameDelimiter = "_"

  override protected def appId(deployment: Deployment, breed: Breed): String = s"/vamp$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  private val dockerMinimumMemory = 4 * 1024 * 1024

  private val logger = Logger(LoggerFactory.getLogger(classOf[DockerDriver]))

  lazy val timeout = ConfigFactory.load().getInt("vamp.core.container-driver.response-timeout") seconds

  private val docker = tugboat.Docker()

  def info: Future[ContainerInfo] = docker.info().map {
    logger.debug(s"docker get info :$docker")
    ContainerInfo("docker", _)
  }

  def all: Future[List[ContainerService]] = Future({
    logger.debug(s"docker get all")

    // Get all containers & container details
    val details: List[ContainerDetails] =
      for {
        container: Container <- getContainers
        detail: ContainerDetails = getContainerDetails(container.id)
      } yield detail

    // Log which container have been found
    for (detail <- details) logger.debug(s"[ALL] name: ${detail.name} ${
      if (processable(detail.name)) {
        "[Monitored by VAMP]"
      } else {
        "[Non-vamp container]"
      }
    }")

    // Wrap the details in the format Operations needs
    val containerDetails: List[ContainerService] = containerDetails2containerServices(for {detail <- details if processable(detail.name)} yield detail)

    logger.debug("[ALL]: " + containerDetails.toString())
    containerDetails
  })

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) = {
    val id = appId(deployment, service.breed)
    findContainer(id) match {
      case None =>
        logger.info(s"[DEPLOY] Container $id does not exist, needs creating")
        createAndStartContainer(id, deployment, cluster, service)
      case Some(found) if update =>
        logger.info(s"[DEPLOY] Container $id already exists, needs updating")
        updateContainer(id, deployment, cluster, service)
      case Some(found) =>
        logger.warn(s"[DEPLOY] Container $id already exists, no action")
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
          removeScale(id)
          val container = docker.containers.get(found.id)
          container.kill()
          container.delete()
        case None =>
          logger.debug(s"[UNDEPLOY] Container $id does not exist")
      }
    )
  }

  /**
   * Blocking method to get a list of all containers
   *
   * @return
   */
  private

  def getContainers: List[Container] = {
    // TODO make this non-blocking
    val dockerContainers: Future[List[Container]] = docker.containers.list()
    Await.result(dockerContainers, timeout)
  }

  /**
   * Blocking method to get details of a single container
   *
   * @param id
   * @return
   */
  private def getContainerDetails(id: String): ContainerDetails = {
    //TODO make this non-blocking
    val containerDetails: Future[ContainerDetails] = docker.containers.get(id)()
    Await.result(containerDetails, timeout)
  }

  /**
   * Get the containerId from a response
   * @param response
   * @return
   */
  private def getContainerId(response: Future[Response]) : Future[String] =
    async{ await(response).id }


  private def containerDetails2containerServices(details: List[ContainerDetails]): List[ContainerService] = {
    val containerMap: Map[String, List[ContainerDetails]] = details map (t => deploymentNameFromContainer(t) -> details.filter(d => deploymentNameFromContainer(d) == deploymentNameFromContainer(t))) toMap

    containerMap.map(deployment =>
      ContainerService(
        matching = nameMatcher(deployment._2.head.name),
        scale = getScale(deployment._2.head.name),
        servers = for {detail <- deployment._2} yield ContainerServer(
          name = serverNameFromContainer(detail),
          host = detail.config.hostname,
          ports = detail.networkSettings.ports.map(port => port._2.map(e => e.hostPort)).flatten.toList,
          deployed = detail.state.running
        )
      )
    ).toList
  }

  private def serverNameFromContainer(container: ContainerDetails): String = {
    val parts = container.name.split(nameDelimiter)
    if (parts.size == 3)
      parts(2)
    else
      container.name
  }

  private def deploymentNameFromContainer(container: ContainerDetails): String = {
    val parts = container.name.split(nameDelimiter)
    if (parts.size == 3)
      parts(1)
    else
      container.name
  }

  private def findContainer(name: String): Option[Container] = {
    val containers = getContainers
    if (containers.isEmpty) None
    else {
      val matching = containers.filter(container => getContainerDetails(container.id).name == name)
      if (matching.isEmpty) None
      else Some(matching.head)
    }
  }


  /**
   * Creates and starts a container
   * If the image is not available, it will be pulled first
   *
   * @param id
   * @param deployment
   * @param cluster
   * @param service
   * @return
   */
  private def createAndStartContainer(id: String, deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) : Future[_] = async {
    val dockerImageName = service.breed.deployable.name

    if( await(docker.images.list()).filter(image => image.id == dockerImageName).isEmpty) {
      pullImage(dockerImageName)
    }

    val ports = portMappings(deployment, cluster, service)
    logger.debug(s"[DEPLOY] ports: $ports")
    val env = environment(deployment, cluster, service)
    val response = createDockerContainer(id, dockerImageName, service.scale, env)
    addScale(getContainerId(response), service.scale)
    startDockerContainer(getContainerId(response), ports)
  }

  /**
   * Pull images from the repo
   * @param name
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
   *
   *  @param containerName
   * @param dockerImageName
   * @param serviceScale
   * @param env
   * @return
   */
  private def createDockerContainer(containerName: String, dockerImageName: String, serviceScale: Option[DefaultScale], env: Map[String, String]): Future[Response] = async {
    val containerWithName = docker.containers.create(dockerImageName).name(containerName)

    var containerPrep = serviceScale match {
      case Some(scale) => containerWithName.cpuShares(scale.cpu.toInt).memory(if (scale.memory.toLong < dockerMinimumMemory) dockerMinimumMemory else scale.memory.toLong)
      case None => containerWithName
    }
    //TODO check if environment variables are set
    for (v <- env) {
      logger.trace(s"[DEPLOY] setting env ${v._1} = ${v._2}")
      containerPrep = containerPrep.env(v)
    }
    await(containerPrep())
  }

  /**
   * Start the container
   * @param id
   * @param ports
   * @return
   */
  private def startDockerContainer(id: Future[String], ports: List[CreatePortMapping]): Future[_] = async {
    // Configure the container for starting
    var startPrep = docker.containers.get(await(id)).start
    for (port <- ports) {
      logger.trace(s"[DEPLOY] setting port: 0.0.0.0:${port.hostPort} -> ${port.containerPort}/tcp")
      startPrep = startPrep.portBind(tugboat.Port.Tcp(port.containerPort), tugboat.PortBinding.local(port.hostPort))
    }
    await(startPrep())
  }

  /**
   * Updates a container already which is already running
   *
   * @param id
   * @param deployment
   * @param cluster
   * @param service
   * @return
   */
  private def updateContainer(id: String, deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    // TODO implement this functionality - need to set ports (and environment??)
    logger.debug(s"[DEPLOY] update ports: ${portMappings(deployment, cluster, service)}")
    addScale(Future(id), service.scale)
    Future(logger.debug("Implement this method"))
  }

}

