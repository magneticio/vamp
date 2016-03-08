package io.vamp.container_driver.docker

import io.vamp.container_driver.{ AbstractContainerDriver, ContainerPortMapping, ContainerInfo, ContainerService, ContainerInstance }
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider }
import io.vamp.model.artifact._

import scala.concurrent.{ ExecutionContext, Future }
import com.spotify.docker.client.{ DockerClient, DefaultDockerClient }
import com.spotify.docker.client.messages.{ HostConfig, PortBinding, ContainerConfig, ContainerInfo ⇒ spContainerInfo, Container ⇒ spContainer, ContainerCreation, Info }

import java.lang.reflect.Field
import java.net.URI

import io.vamp.model.reader.MegaByte

import com.typesafe.config.ConfigFactory

/** This classes come from marathon driver **/
case class DockerParameter(key: String, value: String)
case class Container(docker: Docker, `type`: String = "DOCKER")
case class Docker(image: String, portMappings: List[ContainerPortMapping], parameters: List[DockerParameter], network: String = "BRIDGE")

case class Task(id: String, host: String, ports: List[Int], startedAt: Option[String])
case class App(id: String, instances: Int, cpus: Double, mem: Double, tasks: List[Task])

object RawDockerClient {

  lazy val client = DefaultDockerClient.builder().uri(ConfigFactory.load().getString("vamp.container-driver.url")).build()

  def lift[A, B](f: A ⇒ B): Option[A] ⇒ Option[B] = _ map f
  def Try[A](cl: Class[A], paramKey: String): Either[String, Field] = {
    try {
      Right(cl.getClass.getField(paramKey))
    } catch {
      case e: NoSuchFieldException ⇒ Left(e.getMessage)
      case s: SecurityException    ⇒ Left(s.getMessage)
      case u: Throwable            ⇒ Left(u.getMessage)
    }
  }

  def asyncCall[A, B](f: Option[A] ⇒ Option[B])(implicit ec: ExecutionContext): Option[A] ⇒ Future[Option[B]] = a ⇒ Future { f(a) }

  def internalCreateContainer = lift[(ContainerConfig, String), ContainerCreation] { x ⇒ client.createContainer(x._1, x._2) }
  def internalStartContainer = lift[String, Unit](client.startContainer(_))
  def internalUndeployContainer = lift[String, Unit](client.killContainer(_))
  def internalAllContainer = lift[DockerClient.ListContainersParam, java.util.List[spContainer]]({ client.listContainers(_) })
  def internalInfo = lift[Unit, Info](Unit ⇒ client.info())

  def translateToRaw(container: Option[Container]): Option[ContainerConfig] = {
    val spContainer = ContainerConfig.builder()
    val hostConfig = HostConfig.builder()

    container match {
      case Some(container) ⇒ {
        spContainer.image(container.docker.image)
        container.docker.parameters.map { x ⇒
          Try(spContainer.getClass, x.key) match {
            case Right(field) ⇒ field.set(spContainer, x.value)
            case _            ⇒ None
          }
        }

        val mutableHash: java.util.Map[String, java.util.List[PortBinding]] = new java.util.HashMap[String, java.util.List[PortBinding]]()
        val hostPorts: java.util.List[PortBinding] = new java.util.ArrayList[PortBinding]()

        container.docker.portMappings.map(x ⇒ {
          hostPorts.add(PortBinding.of("0.0.0.0", x.containerPort))
          mutableHash.put(x.containerPort.toString(), hostPorts)
        })

        spContainer.hostConfig(hostConfig.portBindings(mutableHash).build())
        val spCont = spContainer.build()

        Some(spCont)
      }
      case _ ⇒ None
    }
  }

  def translateFromRaw(creation: ContainerCreation): String =
    creation.id

  def translateFromspContainerToApp(container: spContainer): App = {
    val containerName = { if (container.names().size() > 0) container.names().get(0) else "?" }
    App(containerName, 1, 0.0, 0.0, Nil)
  }

  def translateInfoToContainerInfo(info: Info): ContainerInfo = {
    ContainerInfo(info.id(), Unit)
  }
}

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
    asyncCall[Unit, Info](internalInfo).apply(None) map { x ⇒
      x match {
        case Some(info) ⇒ ContainerInfo(info.id(), Unit)
        case None       ⇒ ContainerInfo("???", Unit)
      }
    }
  }

  def all: Future[List[ContainerService]] = {
    asyncCall[DockerClient.ListContainersParam, java.util.List[spContainer]](internalAllContainer).apply(None) map { x ⇒
      x match {
        case Some(containers) ⇒ (containers map (containerService _ compose translateFromspContainerToApp _)).toList
        case None             ⇒ Nil
      }
    }
  }

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any] = {
    val id = appId(deployment, service.breed)
    asyncCall[(ContainerConfig, String), ContainerCreation](internalCreateContainer).apply((translateToRaw(container(deployment, cluster, service))).map { (_, id) }).map { x ⇒
      x match {
        case Some(container) ⇒ internalStartContainer(Some(container.id()))
        case None            ⇒
      }
    }
  }

  def undeploy(deployment: Deployment, service: DeploymentService): Future[Any] = {
    val id = appId(deployment, service.breed)
    asyncCall[String, Unit](internalUndeployContainer).apply(Some(id))
  }
}
