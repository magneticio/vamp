package io.vamp.container_driver.docker

import io.vamp.container_driver.{ AbstractContainerDriver, ContainerPortMapping, ContainerInfo, ContainerService, ContainerInstance }
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider }
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

import spray.json._

/** This classes come from marathon driver **/
case class DockerParameter(key: String, value: String)
case class Container(docker: Docker, `type`: String = "DOCKER")
case class Docker(image: String, portMappings: List[ContainerPortMapping], parameters: List[DockerParameter], network: String = "BRIDGE")

case class Task(id: String, name: String, host: String, ports: List[Int], startedAt: Option[String])
case class App(id: String, instances: Int, cpus: Double, mem: Double, tasks: List[Task])

object RawDockerClient {
  import scala.collection.JavaConversions._

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

  def translateToRaw(container: Option[Container], service: DeploymentService): Option[ContainerConfig] = {
    import DefaultScaleProtocol.DefaultScaleFormat

    val spContainer = ContainerConfig.builder()
    val hostConfig = HostConfig.builder()

    container match {
      case Some(container) ⇒ {
        spContainer.image(container.docker.image)
        val mutableHash: java.util.Map[String, java.util.List[PortBinding]] = new java.util.HashMap[String, java.util.List[PortBinding]]()
        val hostPorts: java.util.List[PortBinding] = new java.util.ArrayList[PortBinding]()

        container.docker.portMappings.map(x ⇒ {
          hostPorts.add(PortBinding.of("0.0.0.0", x.containerPort))
          mutableHash.put(x.containerPort.toString(), hostPorts)
        })

        val labels: MutableMap[String, String] = MutableMap()
        if (service.scale != None)
          labels += ("scale" -> service.scale.get.toJson.toString())

        if (service.dialects.contains(Dialect.Docker)) {
          service.dialects.get(Dialect.Docker).map { x ⇒
            val values = x.asInstanceOf[Map[String, Any]]
            /* Looking for labels */
            val inLabels = values.get("labels").asInstanceOf[Option[Map[String, String]]]
            if (inLabels != None)
              inLabels.get.foreach(f ⇒ { labels += f })
            spContainer.labels(labels)
            /* Getting net parameters */
            val net = values.get("net").asInstanceOf[Option[String]]
            if (net != None) {
                spContainer.hostConfig(hostConfig.portBindings(mutableHash).networkMode(net.get).build())
            } else
              spContainer.hostConfig(hostConfig.portBindings(mutableHash).networkMode("bridge").build())
          }
        }

        val spCont = spContainer.build()
        Some(spCont)
      }
      case _ ⇒ None
    }
  }

  def translateFromRaw(creation: ContainerCreation): String =
    creation.id

  def translateFromspContainerToApp(container: spContainer): App = {
    import DefaultScaleProtocol.DefaultScaleFormat
    import spray.json._

    val containerName = { if (container.names().size() > 0) container.names().head.substring(1) else "?" }
    val defaultScaleJs = container.labels().getOrDefault("scale", "")

    if (!defaultScaleJs.isEmpty()) {
      val defaultScale = defaultScaleJs.parseJson.convertTo[DefaultScale]
      App(containerName, defaultScale.instances, defaultScale.cpu, defaultScale.memory.value, List(Task(container.id(), containerName, "", container.ports().map { x ⇒ x.getPublicPort }.toList, Some(new DateTime(container.created()).toString()))))
    } else {
      /* Default app values. They are going to be stored as label */
      App(containerName, 1, 0.0, 0.0, List(Task(container.id(), containerName, "", container.ports().map { x ⇒ x.getPublicPort }.toList, Some(new DateTime(container.created()).toString()))))
    }
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
    ContainerService(nameMatcher(app.id), DefaultScale("", app.cpus, MegaByte(app.mem), app.instances), app.tasks.map(task ⇒ ContainerInstance(task.name, task.host, task.ports, task.startedAt.isDefined)))

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
    asyncCall[DockerClient.ListContainersParam, java.util.List[spContainer]](internalAllContainer).apply(Some(DockerClient.ListContainersParam.allContainers())) map { x ⇒
      x match {
        case Some(containers) ⇒ (containers.filter { x ⇒ x.status().startsWith("Up") } map (containerService _ compose translateFromspContainerToApp _)).toList
        case None             ⇒ Nil
      }
    }
  }

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any] = {
    val id = appId(deployment, service.breed)
    val asyncResult = asyncCall[DockerClient.ListContainersParam, java.util.List[spContainer]](internalAllContainer).apply(Some(DockerClient.ListContainersParam.allContainers()))
    
    asyncResult map { opList ⇒
      opList match {
        case Some(list) ⇒ list.filter { container ⇒ container.names().toList.head.substring(1) == id }
        case None       ⇒ Nil
      }
    } map { resultList ⇒
      if (resultList.isEmpty)
        (internalCreateContainer).apply((translateToRaw(container(deployment, cluster, service), service)).map { (_, id) }).map { container ⇒ internalStartContainer(Some(container.id())) }
      else
        resultList.toList.map { container ⇒ internalStartContainer(Some(container.id())) }
    }   
  }

  def undeploy(deployment: Deployment, service: DeploymentService): Future[Any] = {
    val name = appId(deployment, service.breed)
    val asyncResult = asyncCall[DockerClient.ListContainersParam, java.util.List[spContainer]](internalAllContainer).apply(Some(DockerClient.ListContainersParam.allContainers()))
    asyncResult map { opList ⇒
      opList match {
        case Some(list) ⇒ list.filter { container ⇒ container.names().toList(0) == name }
        case None       ⇒ Nil
      }
    } map { resultList ⇒
      resultList.toList.map { container ⇒ internalUndeployContainer(Some(container.id())) }
    }
  }
}
