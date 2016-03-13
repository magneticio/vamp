package io.vamp.container_driver.docker

import io.vamp.container_driver.{ AbstractContainerDriver, ContainerPortMapping, ContainerInfo, ContainerService, ContainerInstance }
import io.vamp.model.artifact._
import io.vamp.model.reader.MegaByte

import scala.concurrent.{ ExecutionContext, Future }
import scala.collection.mutable.{ Map ⇒ MutableMap }

import com.spotify.docker.client.{ DockerClient, DefaultDockerClient }
import com.spotify.docker.client.messages.{ HostConfig, PortBinding, ContainerConfig, ContainerInfo ⇒ spContainerInfo, Container ⇒ spContainer, ContainerCreation, Info }

import java.lang.reflect.Field
import java.net.URI

import com.typesafe.config.ConfigFactory

import org.joda.time.DateTime

/** This classes come from marathon driver **/
case class DockerParameter(key: String, value: String)
case class Container(docker: Docker, `type`: String = "DOCKER")
case class Docker(image: String, portMappings: List[ContainerPortMapping], parameters: List[DockerParameter], network: String = "BRIDGE")

case class Task(id: String, name: String, host: String, ports: List[Int], startedAt: Option[String])
case class App(id: String, instances: Int, cpus: Double, mem: Double, tasks: List[Task])

/**
 * Docker driver
 *
 * Docker clients: https://docs.docker.com/engine/reference/api/remote_api_client_libraries/
 * Seems that Java clients are more up to date than Scala.
 *
 */
class DockerDriver(ec: ExecutionContext) extends AbstractContainerDriver(ec) /*extends ContainerDriver with ContainerDriverNotificationProvider*/ {

  import RawDockerClient._
  import scala.collection.JavaConversions._

  /** method from abstract AbstractContainerDriver does not work **/
  override val nameDelimiter = "_"
  override def appId(deployment: Deployment, breed: Breed): String = s"vamp$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"
  override def nameMatcher(id: String): (Deployment, Breed) ⇒ Boolean = { (deployment: Deployment, breed: Breed) ⇒ id == appId(deployment, breed) }

  /** Duplicate code from Marathon **/
  private def containerService(app: App): ContainerService =
    ContainerService(nameMatcher(app.id), DefaultScale("", app.cpus, MegaByte(app.mem), app.instances), app.tasks.map(task ⇒ ContainerInstance(task.id, task.host, task.ports, task.startedAt.isDefined)))

  private def parameters(service: DeploymentService): List[DockerParameter] = service.arguments.map { argument ⇒
    DockerParameter(argument.key, argument.value)
  }

  private def container(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Option[Container] = service.breed.deployable match {
    case Deployable(schema, Some(definition)) ⇒ Some(Container(Docker(definition, portMappings(deployment, cluster, service), parameters(service))))
    case _                                    ⇒ None
  }

  def info: Future[ContainerInfo] = {
    client.asyncCall[Unit, Info](client.internalInfo).apply(None) map { x ⇒
      x match {
        case Some(info) ⇒ ContainerInfo(info.id(), Unit)
        case None       ⇒ ContainerInfo("???", Unit)
      }
    }
  }

  def all: Future[List[ContainerService]] = {
    client.asyncCall[DockerClient.ListContainersParam, java.util.List[spContainer]](client.internalAllContainer).apply(Some(DockerClient.ListContainersParam.allContainers())) map { x ⇒
      x match {
        case Some(containers) ⇒ (containers.filter { x ⇒ x.status().startsWith("Up") } map (containerService _ compose translateFromspContainerToApp _)).toList
        case None             ⇒ Nil
      }
    }
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

  private def client = new RawDockerClient(DefaultDockerClient.builder().uri(ConfigFactory.load().getString("vamp.container-driver.url")).build())
}
