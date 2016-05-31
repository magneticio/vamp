package io.vamp.container_driver.docker

import io.vamp.container_driver._
import io.vamp.model.artifact._
import io.vamp.model.reader.{ MegaByte, Quantity }

import scala.concurrent.{ ExecutionContext, Future }
import scala.collection.mutable.{ Map ⇒ MutableMap }
import com.spotify.docker.client.{ DefaultDockerClient, DockerCertificates, DockerClient }
import com.spotify.docker.client.messages.{ ContainerConfig, ContainerCreation, HostConfig, Info, PortBinding, Container ⇒ spContainer, ContainerInfo ⇒ spContainerInfo }
import java.lang.reflect.Field
import java.net.URI
import java.nio.file.Paths

import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import akka.actor.ActorSystem
import io.vamp.common.crypto.Hash

case class Task(id: String, name: String, host: String, ports: List[Int], startedAt: Option[String])
case class App(id: String, instances: Int, cpus: Double, mem: Double, tasks: List[Task])

/**
 * Docker driver
 *
 * Docker clients: https://docs.docker.com/engine/reference/api/remote_api_client_libraries/
 * Seems that Java clients are more up to date than Scala.
 *
 */
trait DockerDriver extends ContainerDriver {

  import RawDockerClient._
  import scala.collection.JavaConversions._

  protected override val nameDelimiter = "_"

  protected val idMatcher = """^(([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])\\.)*([a-z0-9]|[a-z0-9][a-z0-9\\-]*[a-z0-9])$""".r

  protected override def appId(deployment: Deployment, breed: Breed): String = s"vamp$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"

  protected def artifactName2Id(artifact: Artifact): String = artifact.name match {
    case idMatcher(_*) ⇒ artifact.name
    case _             ⇒ Hash.hexSha1(artifact.name).substring(0, 20)
  }

  protected override def nameMatcher(id: String): (Deployment, Breed) ⇒ Boolean = { (deployment: Deployment, breed: Breed) ⇒ id == appId(deployment, breed) }

  /** Duplicate code from Marathon **/
  private def containerService(app: App): ContainerService =
    ContainerService(nameMatcher(app.id), DefaultScale("", Quantity(app.cpus), MegaByte(app.mem), app.instances), app.tasks.map(task ⇒ ContainerInstance(task.id, task.host, task.ports, task.startedAt.isDefined)))

  private def parameters(service: DeploymentService): List[DockerParameter] = service.arguments.map { argument ⇒
    DockerParameter(argument.key, argument.value)
  }

  private def getContainerInfo(container: ContainerService): ContainerService = {
    val info = client.internalGetContainerInfo(Some(container.instances.head.name))
    if (info != None) {
      container.copy(instances = container.instances.map { x ⇒
        x.copy(host = {
          if (info.get.networkSettings().ipAddress() != null && !info.get.networkSettings().ipAddress().isEmpty())
            info.get.networkSettings().ipAddress()
          else ""
        }, ports = { if (info.get.networkSettings().ports() != null && info.get.networkSettings().ports().size() > 0) info.get.networkSettings().ports().map(f ⇒ f._1.split("/")(0)).toList map { x ⇒ x.toInt } else List(0) })
      })
    } else container
  }

  private def container(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Option[Container] = service.breed.deployable match {
    case Deployable(schema, Some(definition)) ⇒ Some(Container(Docker(definition, portMappings(deployment, cluster, service), parameters(service))))
    case _                                    ⇒ None
  }

  private def getRancherIp(list: List[ContainerService]): Future[List[ContainerService]] = {
    Future {
      if (isRancherEnvironment)
        list.map { container ⇒
          val x = client.internalCallCommand(Some(container.instances.head.name))
          if (x != None && ipAdddr.pattern.matcher(x.get).matches()) {
            container.copy(instances = container.instances.map { i ⇒ i.copy(host = x.get) })
          } else {
            container
          }
        }
      else list
    }
  }

  def info: Future[ContainerInfo] = {
    client.asyncCall[Unit, Info](client.internalInfo).apply(None) map { x ⇒
      x match {
        case Some(info) ⇒ ContainerInfo(info.id(), Unit)
        case None       ⇒ ContainerInfo("Docker", Unit)
      }
    }
  }

  case class ClassDecl(kind: Kind, list: ContainerService)
  sealed trait Kind
  case object Complex extends Kind

  def all: Future[List[ContainerService]] = {
    client.asyncCall[DockerClient.ListContainersParam, java.util.List[spContainer]](client.internalAllContainer).apply(Some(DockerClient.ListContainersParam.allContainers())) map { x ⇒
      x match {
        case Some(containers) ⇒ (containers.filter { x ⇒ x.status().startsWith("Up") } map (containerService _ compose translateFromspContainerToApp _)).toList map getContainerInfo
        case None             ⇒ Nil
      }
    } flatMap getRancherIp
  }

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any] = {
    val id = appId(deployment, service.breed)
    val dockerContainer = container(deployment, cluster, service)

    client.asyncCall[String, Unit](client.pullImage).apply(dockerContainer.map { c ⇒ c.docker.image }) flatMap { unit ⇒
      client.asyncCall[DockerClient.ListContainersParam, java.util.List[spContainer]](client.internalAllContainer).apply(Some(DockerClient.ListContainersParam.allContainers())) map { opList ⇒
        opList match {
          case Some(list) ⇒ list.filter { container ⇒ container.names().toList.head.substring(1) == id }
          case None       ⇒ List()
        }
      } map { resultList ⇒
        if (resultList.isEmpty)
          (client.internalCreateContainer).apply((translateToRaw(dockerContainer, service)).map { (_, id) }).flatMap { container ⇒ client.internalStartContainer(Some(container.id())) }
        else
          resultList.toList.map { container ⇒ client.internalStartContainer(Some(container.id())) }.head
      }
    }
  }

  def undeploy(deployment: Deployment, service: DeploymentService): Future[Any] = {
    val name = appId(deployment, service.breed)
    client.asyncCall[DockerClient.ListContainersParam, java.util.List[spContainer]](client.internalAllContainer).apply(Some(DockerClient.ListContainersParam.allContainers())) map { opList ⇒
      opList match {
        case Some(list) ⇒ list.filter { container ⇒ container.names().toList.head.substring(1) == name }
        case None       ⇒ { Nil }
      }
    } map { resultList ⇒
      resultList.toList.map { container ⇒ client.internalUndeployContainer(Some(container.id())) }
    }
  }

  private val cFactory = ConfigFactory.load()
  private val vampContainerDriverUrl = cFactory.getString("vamp.container-driver.docker.url")
  private val isRancherEnvironment = cFactory.getBoolean("vamp.container-driver.docker.isInRancher")
  private val ipAdddr = """^(?:[0-9]{1,3}\.){3}[0-9]{1,3}$""".r
  private def client = new RawDockerClient(DefaultDockerClient.builder().uri(vampContainerDriverUrl).build())

}
