package io.vamp.container_driver.rancher

import io.vamp.container_driver.rancher.api.{ Service ⇒ RancherService, LaunchConfig, RancherResponse, ServiceList, RancherContainer, RancherContainerPortList, ProjectInfo, ServiceContainersList, Stacks, UpdateService }
import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._
import io.vamp.common.http.RestClient
import io.vamp.model.artifact._
import io.vamp.container_driver.{ ContainerDriver, ContainerPortMapping, ContainerInfo, ContainerService, ContainerInstance }
import io.vamp.container_driver.rancher.api.{ Stack }
import io.vamp.model.reader.MegaByte
import org.json4s._
import org.json4s.{ DefaultFormats, Extraction, Formats }
import akka.pattern.after
import akka.actor.{ Scheduler, ActorSystem }
import com.typesafe.config.ConfigFactory
import scala.language.postfixOps
import org.joda.time.DateTime
import com.google.common.io.BaseEncoding
import com.google.common.base.Charsets

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
    val dpayload = Extraction.decompose(payload)
    dpayload
  }

  /** Duplicate code from Marathon **/
  private def containerService(app: App): ContainerService =
    ContainerService(nameMatcher(app.id), DefaultScale("", app.cpus, MegaByte(app.mem), app.instances), app.tasks.map(task ⇒ ContainerInstance(task.id, task.host, task.ports, task.startedAt.isDefined)))

  private def retry[T](op: ⇒ Future[T], delay: FiniteDuration, retries: Int)(s: Scheduler): Future[T] =
    op recoverWith { case _ if retries > 0 ⇒ after(delay, s)(retry(op, delay, retries - 1)(s)) }

  private[rancher] def getRancherStack(cluster: DeploymentCluster): Stack = {
    Stack(None, None, cluster.name, None)
  }

  private[rancher] def parameters(service: DeploymentService): List[DockerParameter] = service.arguments.map { argument ⇒
    DockerParameter(argument.key, argument.value)
  }

  private[rancher] def container(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Option[Container] = service.breed.deployable match {
    case Deployable(schema, Some(definition)) ⇒ Some(Container(Docker(definition, portMappings(deployment, cluster, service), parameters(service))))
    case _                                    ⇒ None
  }

  private[rancher] def UnitService(name: String): RancherService = {
    RancherService(None, "", None, name, None, None, None, None)
  }

  private[rancher] def getRancherService(environment: String, deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): RancherService = {
    val dockerContainer = container(deployment, cluster, service)
    val id = appId(deployment, service.breed)
    val rs = RancherService(None, environment, None, id, Some(service.scale.get.instances), Some(LaunchConfig(s"docker:${dockerContainer.get.docker.image}", None, false, Some(service.scale.get.cpu.toInt.toString), Some(service.scale.get.memory.value.toString.replace("MB", "").toDouble.toInt.toString))), None)
    rs
  }

  private[rancher] def publishService(rancherService: RancherService, environment: String): Future[RancherService] = {
    /* Check if is update or create */
    RestClient.post[RancherService](s"${vampContainerDriverUrl}/environment/${environment}/services", { requestPayload(rancherService) }, List(authorization)).recover { case e: Exception ⇒ println("ERROR PUBLISH: " + e); UnitService("Error") }
  }

  private[rancher] def activateService(service: RancherService): Future[RancherService] = {
    RestClient.post[RancherService](serviceActivationUrl(service.id.get), None, List(authorization)).recover { case e: Exception ⇒ UnitService("Error") }
  }

  private[rancher] def getServicesList(): Future[ServiceList] = {
    RestClient.get[ServiceList](serviceListUrl, List(authorization)) map { list ⇒
      list.data
    } flatMap getServiceContainers map (ServiceList(_))
  }

  private[rancher] def getContainerPorts(containers: List[RancherContainer]): Future[List[RancherContainer]] = {
    Future.sequence {
      containers map { container ⇒
        RestClient.get[RancherContainerPortList](containerPortUrl(container.id), List(authorization)).map {
          portsList ⇒ { container.copy(ports = portsList.data) }
        }
      }
    }
  }

  private[rancher] def getServiceContainers(serviceList: List[RancherService]): Future[List[RancherService]] = {
    Future.sequence {
      serviceList map { service ⇒
        RestClient.get[ServiceContainersList](serviceContainersListUrl(service.id.get), List(authorization)) flatMap { list ⇒ getContainerPorts(list.data) } map {
          conts ⇒ {
              service.copy(containers = Some(conts))
            }
        }
      }
    }
  }

  private[rancher] def addPortsToTask(task: Task, list: RancherContainerPortList): Task = task.copy(ports = list.data map { port ⇒ port.privatePort.get.toInt })

  private[rancher] def getTasksFromServiceContainerslist(containers: List[RancherContainer]): List[Task] = containers map { container ⇒
    Task(container.id, container.name, container.primaryIpAddress, Nil, Some(container.created))
    addPortsToTask(Task(container.id, container.name, { if (container.primaryIpAddress == null) "" else container.primaryIpAddress }, Nil, Some(container.created)), RancherContainerPortList(container.ports))
  }

  private[rancher] def translateServiceToApp(service: RancherService): App = {
    App(service.name, service.scale.getOrElse(1), { if (service.launchConfig != None) service.launchConfig.get.cpuSet.getOrElse("0.0").toDouble else 0.0 }, { if (service.launchConfig != None) service.launchConfig.get.memoryMb.getOrElse("0.0").toDouble else 0.0 }, getTasksFromServiceContainerslist(service.containers.getOrElse(Nil)))
  }

  private[rancher] def checkIfStackIsActive(stack: Stack): Future[Stack] = {
    val s1 = after[Stack](1 seconds, as.scheduler) { RestClient.get[Stack](s"${environmentsUrl}/${stack.id.get}", List(authorization)) }
    s1 flatMap { f ⇒
      f.state match {
        case Some("active") ⇒ s1
        case _              ⇒ checkIfStackIsActive(f)
      }
    }
  }

  private[rancher] def checkIfServiceIsInactive(service: RancherService): Future[RancherService] = {
    val s1 = after[RancherService](1 seconds, as.scheduler) { RestClient.get[RancherService](s"${serviceListUrl}/${service.id.get}", List(authorization)) }
    s1 flatMap { f ⇒
      f.state match {
        case Some("inactive") ⇒ { activateService(service) }
        case _                ⇒ checkIfServiceIsInactive(f)
      }
    }
  }

  private[rancher] def checkIfServiceIsActive(service: RancherService): Future[RancherService] = {
    val s1 = after[RancherService](1 seconds, as.scheduler) { RestClient.get[RancherService](s"${serviceListUrl}/${service.id.get}", List(authorization)) }
    s1 flatMap { f ⇒
      f.state match {
        case Some("active") ⇒ { s1 }
        case _              ⇒ { checkIfServiceIsActive(f) }
      }
    }
  }

  private[rancher] def checkIfServiceIsAlive(service: RancherService): Future[RancherService] = {
    val s1 = after[RancherService](1 seconds, as.scheduler) { RestClient.get[RancherService](s"${serviceListUrl}/${service.id.get}", List(authorization)) }
    s1 flatMap { f: RancherService ⇒
      f.state match {
        case _ ⇒ { checkIfServiceIsActive(f) }
      }
    } recover {
      case e: Throwable ⇒ {
        UnitService("???")
      }
    }
  }

  private[rancher] def checkIfStackIsCreated(stack: Stack): Future[Stack] = {
    RestClient.get[Stacks](environmentsUrl, List(authorization)).map { list ⇒
      val outStack = list.data.filter { x ⇒ x.name == stack.name }
      if (outStack.isEmpty)
        stack
      else
        outStack(0)
    }
  }

  private[rancher] def createStack(stack: Stack): Future[Stack] = {
    stack.id match {
      case Some(id) ⇒ Future(stack)
      case _        ⇒ RestClient.post[Stack](s"$vampContainerDriverUrl/environment", { requestPayload(stack) }, List(authorization))
    }
  }

  private[rancher] def createIfNotExistStack(cluster: DeploymentCluster): Future[Stack] = {
    val stack = getRancherStack(cluster)
    for {
      s1 ← checkIfStackIsCreated(stack)
      s2 ← createStack(s1)
      s3 ← checkIfStackIsActive(s2)
    } yield s3
  }

  def info: Future[ContainerInfo] =
    RestClient.get[ProjectInfo](vampContainerDriverUrl, List(authorization)).map { project ⇒
      project.state match {
        case "active" ⇒ ContainerInfo(s"Rancher. Project id: ${project.id} is active", Unit)
        case _        ⇒ ContainerInfo("Rancher project is NOT Active!!", Unit)
      }
    }

  def all: Future[List[ContainerService]] = {
    val all = getServicesList map { list ⇒ list.data map { containerService _ compose translateServiceToApp _ } }
    all
  }

  def allApps: Future[ServiceList] = getServicesList

  private[rancher] def checkIfServiceExist(service: RancherService): Future[RancherService] = {
    RestClient.get[ServiceList](serviceListUrl, List(authorization)) map {
      data ⇒ {
          val list = data.data filter (srv ⇒ srv.name == service.name)
          if (list != None && !list.isEmpty) {
            /* If service exists update scale parameters **/
            list.head.copy(scale = service.scale)
          } else {
            service
          }
        }
    }
  }

  private[rancher] def updateService(service: RancherService): Future[RancherService] = {
    val updateUrl = service.actions.get("update")
    RestClient.put[RancherService](updateUrl, requestPayload(UpdateService(service.scale.get)), List(authorization))
  }

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any] = {
    createIfNotExistStack(cluster) map {
      stack ⇒
        /* Manage exception **/
        val rancherService = getRancherService(stack.id.get, deployment, cluster, service)
        checkIfServiceExist(rancherService) flatMap { s ⇒ {
            if (s.id == None)
              publishService(rancherService, stack.id.get)
            else {
              updateService(s)
            }
          } flatMap checkIfServiceIsInactive flatMap checkIfServiceIsActive
        }
    }
  }

  def deployApp(service: RancherService, update: Boolean): Future[Any] = {
    if (update: Boolean) {
      RestClient.put[RancherService](service.actions.get("update"), requestPayload(UpdateService(service.scale.get)), List(authorization)) flatMap checkIfServiceIsInactive flatMap checkIfServiceIsActive
    } else Future {}
  }

  def checkCredentials(credentials: List[(String, String)]): List[(String, String)] =
    credentials.dropWhile(p ⇒ p._1.isEmpty())

  def undeploy(deployment: Deployment, service: DeploymentService): Future[Any] = {
    val id = appId(deployment, service.breed)
    val foundService = getServicesList map { services ⇒ services.data filter { _.name == id } }
    foundService map { list ⇒
      list map { s ⇒
        RestClient.delete(serviceUndeployUrl(s.id.get), List(authorization)) flatMap { r ⇒ checkIfServiceIsAlive(r) }
      }
    }
  }

  private lazy val as = ActorSystem("rancher-retry")

  private lazy val cFactory = ConfigFactory.load()

  private val vampContainerDriverUrl = cFactory.getString("vamp.container-driver.rancher.url")
  private lazy val apiUser: String = cFactory.getString("vamp.container-driver.rancher.user")
  private lazy val apiPassword = cFactory.getString("vamp.container-driver.rancher.password")

  private def serviceActivationUrl(serviceId: String) = s"${vampContainerDriverUrl}/services/${serviceId}/?action=activate"
  private def serviceUndeployUrl(serviceId: String) = s"${vampContainerDriverUrl}/services/${serviceId}/?action=remove"
  private def containerPortUrl(containerId: String) = s"${vampContainerDriverUrl}/containers/${containerId}/ports"
  private def serviceContainersListUrl(serviceId: String) = s"${vampContainerDriverUrl}/services/${serviceId}/instances"
  private val serviceListUrl = s"${vampContainerDriverUrl}/services"
  private val environmentsUrl = s"${vampContainerDriverUrl}/environments"

  private def authorization: (String, String) = { if (!apiUser.isEmpty() && !apiPassword.isEmpty()) ("Authorization" -> ("Basic " + Credentials.credentials(apiUser, apiPassword))) else ("", "") }

}