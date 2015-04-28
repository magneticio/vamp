package io.vamp.core.container_driver.docker

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import io.vamp.core.container_driver._
import io.vamp.core.container_driver.marathon.api.CreatePortMapping
import io.vamp.core.model.artifact._
import org.slf4j.LoggerFactory
import tugboat.Create.Response
import tugboat._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.async.Async.{async, await}

class DockerDriver(ec: ExecutionContext) extends AbstractContainerDriver {
  protected implicit val executionContext = ec

  private val logger = Logger(LoggerFactory.getLogger(classOf[DockerDriver]))

  override protected val nameDelimiter = "_"

  override protected def appId(deployment: Deployment, breed: Breed): String = s"/vamp$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  /* to be removed once the deployments are cached  */
  private val dockerMinimumMemory = 4 * 1024 * 1024

  //private val defaultCpu =ConfigFactory.load().getDouble("vamp.core.dictionary.default-scale.memory")
  private val defaultMemory = ConfigFactory.load().getDouble("vamp.core.dictionary.default-scale.memory")
  //private val defaultInstances =ConfigFactory.load().getInt("vamp.core.dictionary.default-scale.memory")

  //private val defaultScale: DefaultScale = DefaultScale(name = "", cpu = defaultCpu, memory = defaultMemory, instances = defaultInstances)
  /** **/


  lazy val timeout = ConfigFactory.load().getInt("vamp.core.container-driver.response-timeout") seconds

  private val docker = tugboat.Docker()

  private var deployments: Map[String, Deployment] = Map.empty


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
    println(deployments)
    containerDetails
  })

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) = {
    val id = appId(deployment, service.breed)
    addDeploymentToMap(deployment)
    findContainer(id) match {
      case None =>
        logger.info(s"[DEPLOY] Container $id does not exist, needs creating")
        createContainer(id, deployment, cluster, service)
      case Some(found) if update =>
        logger.info(s"[DEPLOY] Container $id already exists, needs updating")
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
   * TODO make this non-blocking
   * @return
   */
  private

  def getContainers: List[Container] = {
    val dockerContainers: Future[List[Container]] = docker.containers.list()
    Await.result(dockerContainers, timeout)
  }

  /**
   * Blocking method to get details of a single container
   * TODO make this non-blocking
   * @param id
   * @return
   */
  private def getContainerDetails(id: String): ContainerDetails = {
    val containerDetails: Future[ContainerDetails] = docker.containers.get(id)()
    Await.result(containerDetails, timeout)
  }

  /**
   * Blocking method to get a list of all images
   * TODO make this non-blocking
   * @return
   */
  private

  def getImages: List[Image] = {
    val dockerImages: Future[List[Image]] = docker.images.list()
    Await.result(dockerImages, timeout)
  }


  private def containerDetails2containerServices(details: List[ContainerDetails]): List[ContainerService] = {
    val containerMap: Map[String, List[ContainerDetails]] = details map (t => deploymentNameFromContainer(t) -> details.filter(d => deploymentNameFromContainer(d) == deploymentNameFromContainer(t))) toMap

    containerMap.map(deployment =>
      ContainerService(
        matching = nameMatcher(deployment._2.head.name),
        scale = DefaultScale(name = "", cpu = deployment._2.head.config.cpuShares, memory = defaultMemory, instances = 1 /* TODO memory locked, instances set to 1 */),
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
    if (containers.isEmpty) None
    else {
      val matching = containers.filter(container => getContainerDetails(container.id).name == name)
      if (matching.isEmpty) None
      else Some(matching.head)
    }
  }

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

  private def createContainer(id: String, deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    val dockerImageName = service.breed.deployable.name
    val ports = portMappings(deployment, cluster, service)
    logger.debug(s"[DEPLOY] ports: $ports")
    val env = environment(deployment, cluster, service)

    val images = getImages
    val matchingNames = images.filter(image => image.id == dockerImageName)
    if (matchingNames.isEmpty) {
      pullImage(dockerImageName)
    }

    val response = createDockerContainer(id, dockerImageName,service.scale, env)
    startDockerContainer(response, ports)
  }

  private def createDockerContainer(containerName: String, dockerImageName: String, serviceScale: Option[DefaultScale], env : Map[String, String]) : Future[Response] = async {
    val containerWithName = docker.containers.create(dockerImageName).name(containerName)

    var containerPrep = serviceScale match {
      case Some(scale) => containerWithName.cpuShares(scale.cpu.toInt).memory(if (scale.memory.toLong < dockerMinimumMemory) dockerMinimumMemory else scale.memory.toLong)
      case None => containerWithName
    }
    // TODO check if environment variables are set
    for (v <- env) {
      logger.trace(s"[DEPLOY] setting env ${v._1} = ${v._2}")
      containerPrep = containerPrep.env(v)
    }
    await(containerPrep())
  }

  private def startDockerContainer(response: Future[Response], ports:  List[CreatePortMapping]) : Future[_] = async {
    // Configure the container for starting
    var prepStart = docker.containers.get(await(response).id).start
    for (port <- ports) {
      logger.trace(s"[DEPLOY] setting port: 0.0.0.0:${port.hostPort} -> ${port.containerPort}/tcp")
      prepStart = prepStart.portBind(tugboat.Port.Tcp(port.containerPort), tugboat.PortBinding.local(port.hostPort))
    }
    await(prepStart())
  }




  private def updateContainer(id: String, deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) = {
    // TODO implement this functionality
    logger.debug(s"[DEPLOY] update ports: ${portMappings(deployment, cluster, service)}")
    Future(logger.debug("Implement this method"))
  }

  private def addDeploymentToMap(deployment: Deployment): Unit = {
    deployments = deployments.filterKeys(key => key != deployment.name) + (deployment.name -> deployment)
  }

  private def getSlaFromDeployment(deploymentName: String, clusterName: String, serviceName: String): Option[DefaultScale] = {
    (for {
      deployment <- deployments.get(deploymentName)
      cluster = deployment.clusters.filter(cluster => cluster.name == clusterName).head
      service = cluster.services.filter(service => service.breed.name == serviceName).head
    } yield service.scale) match {
      case scales => scales.head
      case _ => None
    }
  }
}

