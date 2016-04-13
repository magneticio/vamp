package io.vamp.container_driver.rancher

import io.vamp.container_driver.rancher.api.{ Service ⇒ RancherService, LaunchConfig, RancherResponse, ServiceList, RancherContainer, RancherContainerPortList, ProjectInfo, ServiceContainersList }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

import io.vamp.common.http.RestClient
import io.vamp.model.artifact._
import io.vamp.container_driver.{ ContainerDriver, ContainerPortMapping, ContainerInfo, ContainerService, ContainerInstance }
import io.vamp.container_driver.rancher.api.{ Stack }
import io.vamp.model.reader.MegaByte

import org.json4s.{ DefaultFormats, Extraction, Formats }

import akka.pattern.after
import akka.actor.{ Scheduler, ActorSystem }

import com.typesafe.config.ConfigFactory

import scala.language.postfixOps

case class Task(id: String, name: String, host: String, ports: List[Int], startedAt: Option[String])
case class App(id: String, instances: Int, cpus: Double, mem: Double, tasks: List[Task])
case class DockerParameter(key: String, value: String)
case class Container(docker: Docker, `type`: String = "DOCKER")
case class Docker(image: String, portMappings: List[ContainerPortMapping], parameters: List[DockerParameter], network: String = "BRIDGE")

trait RancherDriver extends ContainerDriver {

  override val nameDelimiter = "-"
  override def appId(deployment: Deployment, breed: Breed): String = s"vamp$nameDelimiter${artifactName2Id(deployment)}$nameDelimiter${artifactName2Id(breed)}"
  override def nameMatcher(id: String): (Deployment, Breed) ⇒ Boolean = { (deployment: Deployment, breed: Breed) ⇒ id == appId(deployment, breed) }

  private[rancher] def requestPayload[A](payload: A) = {
    implicit val formats: Formats = DefaultFormats
    Extraction.decompose(payload)
  }

  /** Duplicate code from Marathon **/
  private def containerService(app: App): ContainerService =
    ContainerService(nameMatcher(app.id), DefaultScale("", app.cpus, MegaByte(app.mem), app.instances), app.tasks.map(task ⇒ ContainerInstance(task.id, task.host, task.ports, task.startedAt.isDefined)))

  private def retry[T](op: ⇒ Future[T], delay: FiniteDuration, retries: Int)(s: Scheduler): Future[T] =
    op recoverWith { case _ if retries > 0 ⇒ after(delay, s)(retry(op, delay, retries - 1)(s)) }

  private[rancher] def getRancherStack(cluster: DeploymentCluster): Stack = {
    Stack(None, cluster.name, None)
  }

  private[rancher] def parameters(service: DeploymentService): List[DockerParameter] = service.arguments.map { argument ⇒
    DockerParameter(argument.key, argument.value)
  }

  private[rancher] def container(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Option[Container] = service.breed.deployable match {
    case Deployable(schema, Some(definition)) ⇒ Some(Container(Docker(definition, portMappings(deployment, cluster, service), parameters(service))))
    case _                                    ⇒ None
  }

  private[rancher] def getRancherService(environment: String, deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): RancherService = {
    val dockerContainer = container(deployment, cluster, service)
    val id = appId(deployment, service.breed)
    RancherService(None, environment, None, id, service.scale.get.instances, Some(LaunchConfig(s"docker:${dockerContainer.get.docker.image}", None)), None)
  }

  private[rancher] def publishService(rancherService: RancherService, environment: String): Future[RancherService] = {
    RestClient.post[RancherService](s"${vampContainerDriverUrl}/environment/${environment}/services", requestPayload(rancherService))
  }

  private[rancher] def activateService(serviceId: String): Future[RancherResponse] =
    RestClient.post[RancherResponse](serviceActivationUrl(serviceId), None)

  private[rancher] def getServicesList(): Future[ServiceList] = {
    RestClient.get[ServiceList](serviceListUrl) map { list ⇒
      list.data
    } flatMap getServiceContainers map (ServiceList(_))
  }

  private[rancher] def getContainerPorts(containers: List[RancherContainer]): Future[List[RancherContainer]] = {
    Future.sequence {
      containers map { container ⇒
        RestClient.get[RancherContainerPortList](containerPortUrl(container.id)).map {
          portsList ⇒ { container.copy(ports = portsList.data) }
        }
      }
    }
  }

  private[rancher] def getServiceContainers(serviceList: List[RancherService]): Future[List[RancherService]] = {
    Future.sequence {
      serviceList map { service ⇒
        RestClient.get[ServiceContainersList](serviceContainersListUrl(service.id.get)) flatMap { list ⇒ getContainerPorts(list.data) } map {
          conts ⇒
            {
              service.copy(containers = Some(conts))
            }
        }
      }
    }
  }

  private[rancher] def addPortsToTask(task: Task, list: RancherContainerPortList): Task = task.copy(ports = list.data map { port ⇒ port.privatePort.toInt })

  private[rancher] def getTasksFromServiceContainerslist(containers: List[RancherContainer]): List[Task] = containers map { container ⇒
    Task(container.id, container.name, container.primaryIpAddress, Nil, Some(container.created))
    addPortsToTask(Task(container.id, container.name, container.primaryIpAddress, Nil, Some(container.created)), RancherContainerPortList(container.ports))
  }

  private[rancher] def translateServiceToApp(service: RancherService): App = {
    App(service.id.get, service.scale, service.launchConfig.get.cpuSet.getOrElse("0.0").toDouble, service.launchConfig.get.memoryMb.getOrElse("0.0").toDouble, getTasksFromServiceContainerslist(service.containers.getOrElse(Nil)))
  }

  def info: Future[ContainerInfo] =
    RestClient.get[ProjectInfo](vampContainerDriverUrl).map { project ⇒
      project.state match {
        case "active" ⇒ ContainerInfo(s"Rancher. Project id: ${project.id} is active", Unit)
        case _        ⇒ ContainerInfo("Rancher project is NOT Active!!", Unit)
      }
    }

  def all: Future[List[ContainerService]] = {
    getServicesList map { list ⇒ list.data map { containerService _ compose translateServiceToApp _ } }
  }

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any] = {
    implicit val formats: Formats = DefaultFormats
    RestClient.post[Stack](s"$vampContainerDriverUrl/environment", { Some(cluster) map (requestPayload _ compose getRancherStack) }.get) map {
      stack ⇒
        {
          /* Manage exception **/
          publishService(getRancherService(stack.id.get, deployment, cluster, service), stack.id.get) flatMap {
            service ⇒ retry(activateService(service.id.get), 3 seconds, 3)(as.scheduler)
          }
        }
    }
  }

  def undeploy(deployment: Deployment, service: DeploymentService): Future[Any] = {
    val id = appId(deployment, service.breed)
    RestClient.delete(serviceUndeployUrl(id))
  }

  private lazy implicit val as = ActorSystem("rancher-retry") 
  
  private lazy val cFactory = ConfigFactory.load()
  private val vampContainerDriverUrl = cFactory.getString("vamp.container-driver.url")
  private def serviceActivationUrl(serviceId: String) = s"${vampContainerDriverUrl}/services/${serviceId}/?action=activate"
  private def serviceUndeployUrl(serviceId: String) = s"${vampContainerDriverUrl}/services/${serviceId}/?action=remove"
  private def containerPortUrl(containerId: String) = s"${vampContainerDriverUrl}/containers/${containerId}/ports"
  private def serviceContainersListUrl(serviceId: String) = s"${vampContainerDriverUrl}/services/${serviceId}/instances"
  private val serviceListUrl = s"${vampContainerDriverUrl}/services"

}