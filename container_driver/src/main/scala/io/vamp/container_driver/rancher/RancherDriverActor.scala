package io.vamp.container_driver.rancher

import io.vamp.common.config.Config
import io.vamp.common.http.RestClient
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.ContainerDriverActor._
import io.vamp.container_driver.DockerAppDriver.{ DeployDockerApp, RetrieveDockerApp, UndeployDockerApp }
import io.vamp.container_driver._
import io.vamp.container_driver.docker.{ DockerDriverActor, DockerNameMatcher }
import io.vamp.container_driver.notification.{ UndefinedDockerImage, UnsupportedContainerDriverRequest }
import io.vamp.container_driver.rancher.{ Service ⇒ RancherService }
import io.vamp.model.artifact.{ Deployment, DeploymentCluster, DeploymentService, _ }
import io.vamp.model.reader.{ MegaByte, Quantity }
import org.apache.commons.codec.Charsets
import org.json4s.{ DefaultFormats, Extraction, Formats }

import scala.concurrent.Future
import scala.language.postfixOps

object RancherDriverActor {

  private val config = Config.config("vamp.container-driver.rancher")

  val rancherUrl = config.string("url")
  val apiUser = config.string("user")
  val apiPassword = config.string("password")
  val environmentPrefix = config.string("environment-prefix")
}

class RancherDriverActor extends ContainerDriverActor with ContainerDriver with DockerNameMatcher {

  import RancherDriverActor._

  protected val nameDelimiter = "-"

  private val serviceListUrl = s"$rancherUrl/services"

  private val environmentsUrl = s"$rancherUrl/environments"

  def receive = {

    case InfoRequest                ⇒ reply(info)

    case Get(services)              ⇒ get(services)
    case d: Deploy                  ⇒ reply(deploy(d.deployment, d.cluster, d.service, d.update))
    case u: Undeploy                ⇒ reply(undeploy(u.deployment, u.cluster, u.service))
    case DeployedGateways(gateways) ⇒ reply(deployedGateways(gateways))

    case d: DeployDockerApp         ⇒ reply(deploy(d.app, d.update))
    case u: UndeployDockerApp       ⇒ reply(undeploy(u.app))
    case r: RetrieveDockerApp       ⇒ reply(retrieve(r.app))

    case any                        ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }

  private def info: Future[ContainerInfo] = {
    RestClient.get[ProjectInfo](rancherUrl, headers).map { project ⇒
      ContainerInfo("rancher", Map("url" -> rancherUrl, "project" -> project.id, "active" -> project.state))
    }
  }

  private def get(deploymentServices: List[DeploymentServices]): Unit = {

    log.debug(s"rancher get all")

    val replyTo = sender()

    deploymentServices.flatMap(ds ⇒ ds.services.map((ds.deployment, _))).foreach {

      case (deployment, service) ⇒

        val serviceName = appId(deployment, service.breed)

        RestClient.get[ServiceList](s"$serviceListUrl?name=$serviceName", headers).map { services ⇒

          services.data.find(service ⇒ service.name == serviceName && service.state == Option("active")) match {

            case Some(s) ⇒

              val cpu = Quantity(s.launchConfig.flatMap(_.cpuShares).getOrElse(0).toDouble)
              val memory = MegaByte(s.launchConfig.flatMap(_.memoryMb).getOrElse(0).toDouble)

              RestClient.get[ServiceContainersList](s"$rancherUrl/services/${s.id.get}/instances", headers).map(_.data).map { containers ⇒

                val instances = containers.map { container ⇒

                  val ports = deployment.clusters.find(cluster ⇒ cluster.services.exists(_.breed.name == service.breed.name)).map { cluster ⇒
                    portMappings(deployment, cluster, service).map(_.containerPort)
                  } getOrElse Nil

                  ContainerInstance(container.id, container.primaryIpAddress, ports, container.state == "running")
                }

                val scale = DefaultScale("", cpu, memory, instances.size)

                replyTo ! ContainerService(deployment, service, Option(Containers(scale, instances)))
              }

            case None ⇒ replyTo ! ContainerService(deployment, service, None)
          }
        }
    }
  }

  def deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean): Future[Any] = {
    createStack(s"$environmentPrefix-${deployment.name}-${cluster.name}") map {
      case Some(stack) ⇒ createService(deployment, cluster, service, update, stack)
      case _           ⇒
    }
  }

  private def createStack(name: String): Future[Option[Stack]] = {

    val stackName = string2Id(name)

    RestClient.get[Stacks](s"$environmentsUrl?name=$stackName", headers).flatMap { stacks ⇒

      val stack: Future[Stack] = stacks.data.find(_.name == stackName) match {
        case Some(s) ⇒ Future.successful(s)
        case None ⇒
          log.info(s"Rancher driver - creating stack: $stackName")
          RestClient.post[Stack](environmentsUrl, requestPayload(Stack(None, None, stackName, None)), headers)
      }

      stack.map { case s ⇒ if (s.state.contains("active")) Option(s) else None }
    }
  }

  private def createService(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean, stack: Stack): Future[Service] = {
    createService(stack, buildRancherService(deployment, cluster, service, stack.id.get), (s) ⇒
      if (update) {
        log.info(s"Rancher driver - updating service: ${s.name}")
        val instances = service.scale.map(_.instances).getOrElse(s.scale.getOrElse(1))
        s.actions.get("update") match {
          case Some(url) ⇒ RestClient.put[Service](url, requestPayload(UpdateService(instances)), headers)
          case _         ⇒ Future.successful(s)
        }
      } else Future.successful(s)
    )
  }

  private def createService(stack: Stack, service: Service, update: Service ⇒ Future[Service]): Future[Service] = {
    RestClient.get[ServiceList](s"$serviceListUrl?name=${service.name}", headers) flatMap { services ⇒
      val rs = services.data.find(s ⇒ s.name == service.name && s.state != Option("removed")) match {
        case Some(s) ⇒ update(s)
        case None ⇒
          log.info(s"Rancher driver - creating service: ${service.name}")
          RestClient.post[Service](s"$rancherUrl/environment/${stack.id.get}/services", requestPayload(service), headers)
      }
      rs.flatMap(activateService(_))
    }
  }

  private def buildRancherService(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, stack: String): Service = {

    validateSchemaSupport(service.breed.deployable.schema, DockerDriverActor.Schema)

    val dockerContainer = docker(deployment, cluster, service, service.breed.deployable.definition.getOrElse(throwException(UndefinedDockerImage)))

    val dockerApp = DockerApp(
      id = appId(deployment, service.breed),
      container = Option(dockerContainer),
      instances = service.scale.map(_.instances).getOrElse(1),
      cpu = service.scale.map(_.cpu.value).getOrElse(0),
      memory = service.scale.map(_.memory.value.toInt).getOrElse(0),
      environmentVariables = environment(deployment, cluster, service),
      labels = labels(deployment, cluster, service)
    )

    buildRancherService(stack, dockerApp)
  }

  private def activateService(service: Service): Future[Service] = {
    RestClient.get[Service](s"$serviceListUrl/${service.id.get}", headers).flatMap { rancherService ⇒
      rancherService.state match {
        case Some(state) if state != "active" ⇒
          service.actions.get("activate") match {
            case Some(_) ⇒ RestClient.post[Service](s"$rancherUrl/services/${service.id.get}/?action=activate", None, headers)
            case _       ⇒ Future.successful(service)
          }
        case _ ⇒ Future.successful(service)
      }
    }
  }

  private def undeploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Future[Any] = {

    val serviceName = appId(deployment, service.breed)

    RestClient.get[ServiceList](s"$serviceListUrl?name=$serviceName", headers).
      map(_.data.filter(_.name == serviceName)).
      flatMap { services ⇒
        val deletes = services.map { s ⇒
          log.info(s"Rancher driver - removing service: ${s.name}")
          RestClient.delete(s"$rancherUrl/services/${s.id.get}/?action=remove", headers)
        }
        Future.sequence(deletes)
      } flatMap {
        case some ⇒

          if (cluster.services.size == 1 && cluster.services.head.breed == service.breed) {

            val stackName = string2Id(s"vamp-${deployment.name}-${cluster.name}")

            RestClient.get[Stacks](s"$environmentsUrl?name=$stackName", headers).flatMap { stacks ⇒

              stacks.data.find(_.name == stackName) match {
                case Some(s) ⇒
                  log.info(s"Rancher driver - removing stack: $stackName")
                  RestClient.delete(s"$environmentsUrl/${s.id.get}/?action=remove", headers)

                case None ⇒ Future.successful(some)
              }
            }
          } else Future.successful(some)
      }
  }

  private def deploy(app: DockerApp, update: Boolean): Future[Any] = {
    createStack(environmentPrefix).flatMap {
      case Some(stack) ⇒ createService(stack, buildRancherService(stack.id.get, app), (s) ⇒ { Future.successful(s) })
      case _           ⇒ Future.successful(None)
    }
  }

  private def undeploy(name: String) = {

    val serviceName = string2Id(name)

    RestClient.get[ServiceList](s"$serviceListUrl?name=$serviceName", headers).map {
      _.data.filter(_.name == serviceName)
    } flatMap { services ⇒
      val deletes = services.map { s ⇒
        log.info(s"Rancher driver - removing service: ${s.name}")
        RestClient.delete(s"$rancherUrl/services/${s.id.get}/?action=remove", headers)
      }
      Future.sequence(deletes)
    }
  }

  private def retrieve(name: String): Future[Option[Service]] = {

    val serviceName = string2Id(name)

    RestClient.get[ServiceList](s"$serviceListUrl?name=$serviceName", headers).map {
      case ServiceList(Nil)  ⇒ None
      case ServiceList(list) ⇒ Option(list.head)
    }
  }

  protected def appId(deployment: Deployment, breed: Breed): String = artifactName2Id(breed)

  private def headers: List[(String, String)] = {
    if (!apiUser.isEmpty && !apiPassword.isEmpty)
      "Authorization" -> ("Basic " + credentials(apiUser, apiPassword)) :: RestClient.jsonHeaders
    else
      RestClient.jsonHeaders
  }

  private def credentials(user: String, password: String): String = {
    val encoder = new sun.misc.BASE64Encoder
    val base64Auth = s"$user:$password"
    encoder.encode(base64Auth.getBytes(Charsets.UTF_8)).replace("\n", "")
  }

  private def requestPayload[A](payload: A) = {
    implicit val formats: Formats = DefaultFormats
    Extraction.decompose(payload)
  }

  private def buildRancherService(stack: String, dockerApp: DockerApp): Service = {

    val dockerContainer = dockerApp.container.get

    val id = string2Id(dockerApp.id)

    RancherService(
      state = None,
      environmentId = stack,
      id = None,
      name = id,
      scale = Option(dockerApp.instances),
      launchConfig = Some(LaunchConfig(
        imageUuid = s"docker:${dockerContainer.image}",
        labels = if (dockerApp.labels.isEmpty) None else Option(dockerApp.labels),
        privileged = Option(dockerContainer.privileged),
        startOnCreate = false,
        cpuShares = if (dockerApp.cpu.toInt > 0) Option(dockerApp.cpu.toInt) else None,
        memoryMb = if (dockerApp.memory > 0) Option(dockerApp.memory) else None,
        environment = dockerApp.environmentVariables,
        networkMode = dockerContainer.network.toLowerCase,
        command = dockerApp.command
      )),
      actions = Map(),
      containers = Nil,
      startOnCreate = true
    )
  }
}
