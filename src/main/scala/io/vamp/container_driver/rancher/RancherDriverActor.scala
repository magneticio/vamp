package io.vamp.container_driver.rancher

import com.typesafe.config.ConfigFactory
import io.vamp.common.http.RestClient
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.ContainerDriverActor._
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

  private val configuration = ConfigFactory.load().getConfig("vamp.container-driver.rancher")

  val rancherUrl = configuration.getString("url")
  val apiUser = configuration.getString("user")
  val apiPassword = configuration.getString("password")
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
    createStack(deployment, cluster) map {
      case Some(stack) ⇒ createService(deployment, cluster, service, update, stack)
      case _           ⇒
    }
  }

  private def createStack(deployment: Deployment, cluster: DeploymentCluster): Future[Option[Stack]] = {

    val stackName = string2Id(s"vamp-${deployment.name}-${cluster.name}")

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

  private def createService(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean, stack: Stack) = {

    val serviceName = appId(deployment, service.breed)

    RestClient.get[ServiceList](s"$serviceListUrl?name=$serviceName", headers) flatMap { services ⇒

      val rs = services.data.find(service ⇒ service.name == serviceName && service.state != Option("removed")) match {

        case Some(s) ⇒
          if (update) {
            log.info(s"Rancher driver - updating service: ${s.name}")
            val instances = service.scale.map(_.instances).getOrElse(s.scale.getOrElse(1))
            RestClient.put[Service](s.actions.get("update"), requestPayload(UpdateService(instances)), headers)
          } else Future.successful(s)

        case None ⇒
          val rancherService = buildRancherService(deployment, cluster, service, stack.id.get)
          log.info(s"Rancher driver - creating service: ${rancherService.name}")
          RestClient.post[Service](s"$rancherUrl/environment/${stack.id.get}/services", requestPayload(rancherService), headers)
      }

      rs.map(activateService(_))
    }
  }

  private def buildRancherService(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, stack: String): Service = {

    validateSchemaSupport(service.breed.deployable.schema, DockerDriverActor.Schema)

    val dockerContainer = docker(deployment, cluster, service, service.breed.deployable.definition.getOrElse(throwException(UndefinedDockerImage)))

    val cpu = service.scale.map(_.cpu.value.toInt).getOrElse(0)
    val memory = service.scale.map(_.memory.value.toInt).getOrElse(0)

    val id = appId(deployment, service.breed)
    RancherService(
      state = None,
      environmentId = stack,
      id = None,
      name = id,
      scale = service.scale.map(_.instances),
      launchConfig = Some(LaunchConfig(
        imageUuid = s"docker:${dockerContainer.image}",
        labels = None,
        privileged = Option(dockerContainer.privileged),
        startOnCreate = false,
        cpuShares = if (cpu > 0) Option(cpu) else None,
        memoryMb = if (memory > 0) Option(memory) else None,
        environment = environment(deployment, cluster, service)
      )),
      actions = None,
      containers = None,
      startOnCreate = true
    )
  }

  private def activateService(service: Service) = {
    RestClient.get[Service](s"$serviceListUrl}${service.id.get}", headers).map { rancherService ⇒
      rancherService.state match {
        case Some(state) if state != "active" ⇒ RestClient.post[Service](s"$rancherUrl/services/${service.id.get}/?action=activate", None, headers)
        case _                                ⇒
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
}
