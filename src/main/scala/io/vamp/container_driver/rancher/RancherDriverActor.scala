package io.vamp.container_driver.rancher

import io.vamp.common.config.Config
import io.vamp.common.http.HttpClient
import io.vamp.common.spi.ClassMapper
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.ContainerDriverActor._
import io.vamp.container_driver._
import io.vamp.container_driver.docker.DockerNameMatcher
import io.vamp.container_driver.notification.UnsupportedContainerDriverRequest
import io.vamp.container_driver.rancher.{ Service ⇒ RancherService }
import io.vamp.model.artifact.{ Deployment, DeploymentCluster, DeploymentService, _ }
import io.vamp.model.reader.{ MegaByte, Quantity }
import org.json4s.{ DefaultFormats, Extraction, Formats }

import scala.concurrent.Future

class RancherDriverActorMapper extends ClassMapper {
  val name = "rancher"
  val clazz = classOf[RancherDriverActor]
}

object RancherDriverActor {

  private val config = Config.config("vamp.container-driver.rancher")

  val rancherUrl = config.string("url")
  val workflowNamePrefix = config.string("workflow-name-prefix")
  val apiUser = config.string("user")
  val apiPassword = config.string("password")
  val environmentName = config.string("environment.name")
  val deploymentEnvironmentNamePrefix = config.string("environment.deployment.name-prefix")
}

class RancherDriverActor extends ContainerDriverActor with ContainerDriver with DockerNameMatcher {

  import RancherDriverActor._

  protected val nameDelimiter = "-"

  private val serviceListUrl = s"$rancherUrl/services"

  private val environmentsUrl = s"$rancherUrl/environments"

  private val headers: List[(String, String)] = HttpClient.basicAuthorization(apiUser, apiPassword)

  def receive = {

    case InfoRequest                ⇒ reply(info)

    case Get(services)              ⇒ get(services)
    case d: Deploy                  ⇒ reply(deploy(d.deployment, d.cluster, d.service, d.update))
    case u: Undeploy                ⇒ reply(undeploy(u.deployment, u.cluster, u.service))
    case DeployedGateways(gateways) ⇒ reply(deployedGateways(gateways))

    case GetWorkflow(workflow)      ⇒ reply(retrieve(workflow))
    case d: DeployWorkflow          ⇒ reply(deploy(d.workflow, d.update))
    case u: UndeployWorkflow        ⇒ reply(undeploy(u.workflow))

    case any                        ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }

  override protected def supportedDeployableTypes = DockerDeployable :: Nil

  private def info: Future[ContainerInfo] = {
    httpClient.get[ProjectInfo](rancherUrl, headers).map { project ⇒
      ContainerInfo("rancher", Map("url" → rancherUrl, "project" → project.id, "active" → project.state))
    }
  }

  private def get(deploymentServices: List[DeploymentServices]): Unit = {

    log.debug(s"rancher get all")

    val replyTo = sender()

    deploymentServices.flatMap(ds ⇒ ds.services.map((ds.deployment, _))).foreach {

      case (deployment, service) ⇒

        val serviceName = appId(deployment, service.breed)

        httpClient.get[ServiceList](s"$serviceListUrl?name=$serviceName", headers).map { services ⇒

          services.data.find(service ⇒ service.name == serviceName && service.state == Option("active")) match {

            case Some(s) ⇒

              val cpu = Quantity(s.launchConfig.flatMap(_.cpuShares).getOrElse(0).toDouble)
              val memory = MegaByte(s.launchConfig.flatMap(_.memoryMb).getOrElse(0).toDouble)

              httpClient.get[ServiceContainersList](s"$rancherUrl/services/${s.id.get}/instances", headers).map(_.data).map { containers ⇒

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

    val stackName = if (deploymentEnvironmentNamePrefix.nonEmpty) s"$deploymentEnvironmentNamePrefix${deployment.name}" else deployment.name

    createStack(stackName) map {
      case Some(stack) ⇒ createService(deployment, cluster, service, update, stack)
      case _           ⇒
    }
  }

  private def createStack(name: String): Future[Option[Stack]] = {

    val stackName = string2Id(name)

    httpClient.get[Stacks](s"$environmentsUrl?name=$stackName", headers).flatMap { stacks ⇒

      val stack: Future[Stack] = stacks.data.find(_.name == stackName) match {
        case Some(s) ⇒ Future.successful(s)
        case None ⇒
          log.info(s"Rancher driver - creating stack: $stackName")
          httpClient.post[Stack](environmentsUrl, requestPayload(Stack(None, None, stackName, None)), headers)
      }

      stack.map { s ⇒ if (s.state.contains("active")) Option(s) else None }
    }
  }

  private def createService(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean, stack: Stack): Future[Service] = {
    createService(stack, buildRancherService(deployment, cluster, service, stack.id.get), (s) ⇒
      if (update) {
        log.info(s"Rancher driver - updating service: ${s.name}")
        val instances = service.scale.map(_.instances).getOrElse(s.scale.getOrElse(1))
        s.actions.get("update") match {
          case Some(url) ⇒ httpClient.put[Service](url, requestPayload(UpdateService(instances)), headers)
          case _         ⇒ Future.successful(s)
        }
      }
      else Future.successful(s))
  }

  private def createService(stack: Stack, service: Service, update: Service ⇒ Future[Service]): Future[Service] = {
    httpClient.get[ServiceList](s"$serviceListUrl?name=${service.name}", headers) flatMap { services ⇒
      val rs = services.data.find(s ⇒ s.name == service.name && s.state != Option("removed")) match {
        case Some(s) ⇒ update(s)
        case None ⇒
          log.info(s"Rancher driver - creating service: ${service.name}")
          httpClient.post[Service](s"$rancherUrl/environment/${stack.id.get}/services", requestPayload(service), headers)
      }
      rs.flatMap(activateService)
    }
  }

  private def buildRancherService(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, stack: String): Service = {

    validateDeployable(service.breed.deployable)

    val dockerContainer = docker(deployment, cluster, service)

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
    httpClient.get[Service](s"$serviceListUrl/${service.id.get}", headers).flatMap { rancherService ⇒
      rancherService.state match {
        case Some(state) if state != "active" ⇒
          service.actions.get("activate") match {
            case Some(_) ⇒ httpClient.post[Service](s"$rancherUrl/services/${service.id.get}/?action=activate", None, headers)
            case _       ⇒ Future.successful(service)
          }
        case _ ⇒ Future.successful(service)
      }
    }
  }

  private def undeploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService): Future[Any] = {

    val serviceName = appId(deployment, service.breed)

    httpClient.get[ServiceList](s"$serviceListUrl?name=$serviceName", headers).
      map(_.data.filter(_.name == serviceName)).
      flatMap { services ⇒
        val deletes = services.map { s ⇒
          log.info(s"Rancher driver - removing service: ${s.name}")
          httpClient.delete(s"$rancherUrl/services/${s.id.get}/?action=remove", headers)
        }
        Future.sequence(deletes)
      } flatMap { some ⇒

        if (cluster.services.size == 1 && cluster.services.head.breed == service.breed) {

          val stackName = string2Id(s"vamp-${deployment.name}-${cluster.name}")

          httpClient.get[Stacks](s"$environmentsUrl?name=$stackName", headers).flatMap { stacks ⇒

            stacks.data.find(_.name == stackName) match {
              case Some(s) ⇒
                log.info(s"Rancher driver - removing stack: $stackName")
                httpClient.delete(s"$environmentsUrl/${s.id.get}/?action=remove", headers)

              case None ⇒ Future.successful(some)
            }
          }
        }
        else Future.successful(some)
      }
  }

  private def deploy(workflow: Workflow, update: Boolean): Future[Any] = {
    createStack(environmentName).flatMap {
      case Some(stack) ⇒

        val breed = workflow.breed.asInstanceOf[DefaultBreed]

        validateDeployable(breed.deployable)

        val scale = workflow.scale.get.asInstanceOf[DefaultScale]

        val dockerApp = DockerApp(
          id = appId(workflow),
          container = Option(docker(workflow)),
          instances = scale.instances,
          cpu = scale.cpu.value,
          memory = scale.memory.value.toInt,
          environmentVariables = environment(workflow),
          labels = labels(workflow)
        )

        createService(stack, buildRancherService(stack.id.get, dockerApp), (s) ⇒ {
          Future.successful(s)
        })

      case _ ⇒ Future.successful(None)
    }
  }

  private def undeploy(workflow: Workflow) = {

    val serviceName = appId(workflow)

    httpClient.get[ServiceList](s"$serviceListUrl?name=$serviceName", headers).map {
      _.data.filter(_.name == serviceName)
    } flatMap { services ⇒
      val deletes = services.map { s ⇒
        log.info(s"Rancher driver - removing workflow: ${workflow.name}")
        httpClient.delete(s"$rancherUrl/services/${s.id.get}/?action=remove", headers)
      }
      Future.sequence(deletes)
    }
  }

  private def retrieve(workflow: Workflow): Future[Option[Service]] = {

    val serviceName = appId(workflow)

    httpClient.get[ServiceList](s"$serviceListUrl?name=$serviceName", headers).map {
      case ServiceList(Nil)  ⇒ None
      case ServiceList(list) ⇒ Option(list.head)
    }
  }

  protected def appId(workflow: Workflow): String = s"$workflowNamePrefix${artifactName2Id(workflow)}"

  protected def appId(deployment: Deployment, breed: Breed): String = artifactName2Id(breed)

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
