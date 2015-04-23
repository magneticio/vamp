package io.vamp.core.container_driver.docker

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import io.vamp.common.crypto.Hash
import io.vamp.core.container_driver.marathon.api._
import io.vamp.core.container_driver.{ContainerDriver, ContainerInfo, ContainerServer, ContainerService}
import io.vamp.core.model.artifact._
import org.slf4j.LoggerFactory
import tugboat._

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
    for (detail <- details) logger.debug(s"[ALL] name: ${detail.name} ${if (processable(detail.name)) {"[Monitored by VAMP]" } else {"[Non-vamp container]"} }")

    // Wrap the details in the format Operations needs
    val containerDetails: List[ContainerService] = containerDetails2containerServices(for {detail <- details if processable(detail.name)} yield detail )

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

  /**
   * Blocking method to get a list of all containers
   * @return
   */private def getContainers: List[Container] = {
    val dockerContainers: Future[List[Container]] = docker.containers.list()
    Await.result(dockerContainers, timeout)
  }

  /**
   * Blocking method to get details of a single container
   * @param id
   * @return
   */
  private def getContainerDetails(id: String): ContainerDetails = {
    val containerDetails: Future[ContainerDetails] = docker.containers.get(id)()
    Await.result(containerDetails, timeout)
  }

  /**
   * Blocking method to get a list of all containers
   * @return
   */private def getImages: List[Image] = {
    val dockerImages: Future[List[Image]] = docker.images.list()
    Await.result(dockerImages, timeout)
  }


  private def containerDetails2containerServices(details: List[ContainerDetails]): List[ContainerService] = {
   val containerMap : Map[String, List[ContainerDetails]] = details map (t => deploymentNameFromContainer(t) -> details.filter(d=> deploymentNameFromContainer(d) == deploymentNameFromContainer(t))) toMap

    containerMap.map(deployment=>
        ContainerService(
          matching = nameMatcher(deployment._2.head.name),
          scale = DefaultScale(name = "", cpu = deployment._2.head.config.cpuShares, memory = defaultMemory, instances = 1 /* TODO memory locked, instances set to 1 */),
          servers = for { detail <- deployment._2 } yield ContainerServer(
              name = serverNameFromContainer(detail),
              host = detail.config.hostname,
              ports = detail.networkSettings.ports.map(port => port._2.map(e => e.hostPort)).flatten.toList,
              deployed = detail.state.running
          )
        )
    ).toList
  }

  private def serverNameFromContainer(container: ContainerDetails): String = {
    val parts = container.name.split("_")
    if (parts.size == 3)
      parts(2)
    else
      container.name
  }

  private def deploymentNameFromContainer(container: ContainerDetails): String = {
    val parts = container.name.split("_")
    if (parts.size == 3)
      parts(1)
    else
      container.name
  }

  private def findContainer(name: String): Option[Container] = {
    val containers = getContainers
    if (containers.isEmpty)  None
    else {
      val matching = containers.filter(container => getContainerDetails(container.id).name == name)
      if (matching.isEmpty) None
      else Some(matching.head)
    }
  }

  private def pullImage(name : String): Unit = {
    docker.images.pull(name).stream {
      case Pull.Status(msg) =>  logger.debug(s"[DEPLOY] pulling image $name: $msg")
      case Pull.Progress(msg, _, details) =>
        logger.debug(s"[DEPLOY] pulling image $name: $msg")
        details.foreach { dets =>
          logger.debug(s"[DEPLOY] pulling image $name: ${dets.bar}")
        }
      case Pull.Error(msg, _) =>  logger.error(s"[DEPLOY] pulling image $name failed: $msg")
    }
  }

  private def createContainer(id: String, deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    val dockerImageName = service.breed.deployable.name

    val images = getImages
    val matchingNames = images.filter(image => image.id == dockerImageName)
    if (matchingNames.isEmpty) {
      pullImage(dockerImageName)
    }

    val containerwithName = docker.containers.create(dockerImageName).name(id)

    val containerPrep = service.scale match {
      case Some(scale) => containerwithName.cpuShares(scale.cpu.toInt).memory(if(scale.memory.toLong < dockerMinimumMemory) dockerMinimumMemory else scale.memory.toLong  )
      case None => containerwithName
    }

    // TODO environment variables need to be set

    // Create the actual container [Blocking]
    val container = Await.result(containerPrep(), timeout)

    logger.debug(s"[DEPLOY] ports: ${portMappings(deployment, cluster, service)}")

    // Configure the container for starting
    var prepStart = docker.containers.get(container.id).start
    for (port <- portMappings(deployment, cluster, service)) {
      logger.trace(s"[DEPLOY] setting port: 0.0.0.0:${port.hostPort} -> ${port.containerPort}/tcp")
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
    service.breed.ports.map(port =>
      port.value match {
        case Some(_) => CreatePortMapping(port.number)
        case None => CreatePortMapping(deployment.ports.find(p => TraitReference(cluster.name, TraitReference.Ports, port.name).toString == p.name).get.number)
      })
  }

  private def environment(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Map[String, String] =
    service.breed.environmentVariables.map(ev => ev.alias.getOrElse(ev.name) -> deployment.environmentVariables.find(e => TraitReference(cluster.name, TraitReference.EnvironmentVariables, ev.name).toString == e.name).get.value.get).toMap


  private def appId(deployment: Deployment, breed: Breed): String = s"/vamp$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  private def processable(id: String): Boolean = id.split(nameDelimiter).size == 3

  private def nameMatcher(id: String): (Deployment, Breed) => Boolean = { (deployment: Deployment, breed: Breed) => id == appId(deployment, breed) }

  private def artifactName2Id(artifact: Artifact) = artifact.name match {
    case idMatcher(_*) => artifact.name
    case _ => Hash.hexSha1(artifact.name).substring(0, 20)
  }
}

