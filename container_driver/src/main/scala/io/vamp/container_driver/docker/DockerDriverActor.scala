package io.vamp.container_driver.docker

import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.ContainerDriverActor.{ All, Deploy, Undeploy }
import io.vamp.container_driver._
import io.vamp.container_driver.docker.wrapper.Create.Response
import io.vamp.container_driver.docker.wrapper._
import io.vamp.container_driver.docker.wrapper.model._
import io.vamp.container_driver.notification.{ UndefinedDockerImage, UnsupportedContainerDriverRequest }
import io.vamp.model.artifact._

import scala.async.Async.{ async, await }
import scala.concurrent.Future
import scala.language.postfixOps

object DockerDriverActor {

  object Schema extends Enumeration {
    val Docker = Value
  }

}

class DockerDriverActor extends ContainerDriverActor with ContainerDriver with DummyScales {

  override protected val nameDelimiter = "_"

  override protected def appId(deployment: Deployment, breed: Breed): String = s"/vamp$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  private val dockerMinimumMemory = 4 * 1024 * 1024

  private val docker = wrapper.Docker()

  private val defaultHost = "0.0.0.0"

  def receive = {
    case InfoRequest ⇒ reply(info)
    case All ⇒ reply(all)
    case Deploy(deployment, cluster, service, update) ⇒ reply(deploy(deployment, cluster, service, update))
    case Undeploy(deployment, service) ⇒ reply(undeploy(deployment, service))
    case any ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }

  private def info: Future[Any] = docker.info().map { response ⇒
    log.debug(s"docker get info :$docker")
    ContainerInfo("docker", response)
  }

  private def all: Future[List[ContainerService]] = async {
    log.debug(s"docker get all")

    // Get all containers & container details
    val details: List[Future[ContainerDetails]] =
      for {
        container: Container ← await(docker.containers.list())
        detail: Future[ContainerDetails] = getContainerDetails(container.id)
      } yield detail

    // Log which container have been found
    for (detail ← details) logContainerDetails(detail)

    val actualDetails: List[ContainerDetails] = await(Future.sequence(details))

    val containerDetails: List[ContainerService] = details2Services(for { detail ← actualDetails if processable(detail.name) } yield detail)
    log.debug("[ALL]: " + containerDetails.toString())
    containerDetails
  }

  private def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) = {
    val containerName = appId(deployment, service.breed)
    findContainerIdByName(containerName).map {
      case None ⇒
        log.info(s"[DEPLOY] Container $containerName does not exist, needs creating")
        validateSchemaSupport(service.breed.deployable.schema, DockerDriverActor.Schema)
        createAndStartContainer(containerName, deployment, cluster, service)

      case Some(found) if update ⇒
        log.info(s"[DEPLOY] Container $containerName already exists, needs updating")
        addScale(Future(found), service.scale)

      case Some(found) ⇒
        log.warning(s"[DEPLOY] Container $containerName already exists, no action")
        None
    }
  }

  private def undeploy(deployment: Deployment, service: DeploymentService) = {
    val containerName = appId(deployment, service.breed)
    log.info(s"docker delete app: $containerName")
    findContainerIdByName(containerName).map {
      case Some(found) ⇒
        log.debug(s"[UNDEPLOY] Container $containerName found, trying to kill it")
        removeScale(containerName)
        val container = docker.containers.get(found)
        container.kill()
        container.delete()
      case None ⇒
        log.debug(s"[UNDEPLOY] Container $containerName does not exist")
    }
  }

  private def findContainerIdByName(name: String): Future[Option[String]] = docker.containers.list().flatMap({
    containers ⇒ Future.sequence(containers.map(container ⇒ getContainerDetails(container.id)))
  }).map { details ⇒
    details.find(detail ⇒ processable(detail.name) && detail.name == name).flatMap(detail ⇒ Some(detail.id))
  }

  private def details2Services(details: List[ContainerDetails]): List[ContainerService] = {
    val serviceMap: Map[String, List[ContainerDetails]] = details.groupBy(x ⇒ x.name)
    serviceMap.map({ deployment ⇒
      val details = deployment._2.head
      val scale = getScale(details.id)
      val server = detail2Server(details)

      ContainerService(
        matching = nameMatcher(details.name),
        scale = scale,
        instances = (0 until scale.instances).map(i ⇒ server.copy(name = s"${server.name}[$i]")).toList)
    }).toList
  }

  private def detail2Server(cd: ContainerDetails): ContainerInstance = {
    log.debug(s"Details2Server containerDetails: $cd")
    ContainerInstance(
      name = serverNameFromContainer(cd),
      host = if (cd.config.hostName.isEmpty) defaultHost else cd.config.hostName,
      ports = cd.networkSettings.ports.flatMap(port ⇒ port._2.map(e ⇒ e.hostPort)).toList,
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
    val dialect: Map[Any, Any] = resolveDialect(deployment, cluster, service)

    val dockerImageName = service.breed.deployable match {
      case Deployable(_, Some(definition)) ⇒ definition
      case _                               ⇒ throwException(UndefinedDockerImage)
    }
    val allImages: List[Image] = await(docker.images.list())
    val taggedImages = allImages.filter(image ⇒ image.repoTags.contains(dockerImageName))

    if (taggedImages.isEmpty) {
      pullImage(dockerImageName, dialect)
    }

    val response = createDockerContainer(dialect, containerName, dockerImageName, environment(deployment, cluster, service), portMappings(deployment, cluster, service))

    addScale(getContainerFromResponseId(response), service.scale)

    startDockerContainer(dialect, getContainerFromResponseId(response), portMappings(deployment, cluster, service), service.scale).onFailure {
      case ex ⇒
        log.debug(s"Failed to start docker container: $ex, ${ex.getStackTrace.mkString("\n")}")
    }
  }

  private def resolveDialect(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Map[Any, Any] = {
    val (local, dialect) = (cluster.dialects.get(Dialect.Docker), service.dialects.get(Dialect.Docker)) match {
      case (_, Some(d))    ⇒ Some(service) -> d
      case (Some(d), None) ⇒ None -> d
      case _               ⇒ None -> Map()
    }

    interpolate(deployment, local, dialect.asInstanceOf[Map[Any, Any]])
  }

  /**
   * Pull images from the repo
   */
  private def pullImage(name: String, dialect: Map[Any, Any]): Unit = {
    docker.images.pull(name, dialect).stream {
      case Pull.Status(msg) ⇒ log.debug(s"[DEPLOY] pulling image $name: $msg")
      case Pull.Progress(msg, _, details) ⇒
        log.debug(s"[DEPLOY] pulling image $name: $msg")
        details.foreach { detail ⇒
          log.debug(s"[DEPLOY] pulling image $name: ${detail.bar}")
        }
      case Pull.Error(msg, _) ⇒ log.error(s"[DEPLOY] pulling image $name failed: $msg")
    }
  }

  /**
   * Create a docker container (without starting it)
   * Tries to set the cpu shares & memory based on the supplied scale
   */
  private def createDockerContainer(dialect: Map[Any, Any], containerName: String, dockerImageName: String, env: Map[String, String], ports: List[ContainerPortMapping]): Future[Response] = async {
    log.debug(s"createDockerContainer :$containerName")

    var containerPrep = docker.containers.create(dockerImageName, dialect).name(containerName).hostName(defaultHost)

    for (v ← env) {
      log.debug(s"[CreateDockerContainer] setting env ${v._1} = ${v._2}")
    }
    containerPrep = containerPrep.env(env.toSeq: _*)

    val exposedPorts = ports.map(p ⇒ {
      log.debug(s"[CreateDockerContainer] exposed ports ${p.containerPort}")
      p.containerPort.toString
    })
    containerPrep = containerPrep.exposedPorts(exposedPorts.toSeq: _*)

    await(containerPrep())
  }

  /**
   * Start the container
   */
  private def startDockerContainer(dialect: Map[Any, Any], id: Future[String], ports: List[ContainerPortMapping], serviceScale: Option[DefaultScale]): Future[_] = async {
    // Configure the container for starting

    id.onFailure {
      case ex ⇒
        log.debug(s"Failed to create docker container: $ex, ${ex.getStackTrace.mkString("\n")}")
    }

    var startPrep = docker.containers.get(await(id)).start(dialect)

    for (port ← ports) {
      log.debug(s"[StartContainer] setting port: 0.0.0.0:${port.hostPort} -> ${port.containerPort}/tcp")
      startPrep = startPrep.portBind(wrapper.model.Port.Tcp(port.containerPort), PortBinding.local(port.hostPort))
    }
    startPrep = serviceScale match {
      case Some(scale) ⇒ startPrep.cpuShares(scale.cpu.toInt).memory(if (scale.memory.value.toLong < dockerMinimumMemory) dockerMinimumMemory else scale.memory.value.toLong)
      case None        ⇒ startPrep
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
    log.debug(s"[ALL] name: ${await(detail).name} ${
      if (processable(await(detail).name)) {
        "[Monitored by VAMP]"
      } else {
        "[Non-vamp container]"
      }
    }")
  }

}

