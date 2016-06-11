package io.vamp.container_driver.kubernetes

import com.typesafe.config.ConfigFactory
import io.vamp.common.http.RestClient
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.ContainerDriverActor._
import io.vamp.container_driver.DockerAppDriver.{ DeployDockerApp, RetrieveDockerApp, UndeployDockerApp }
import io.vamp.container_driver._
import io.vamp.container_driver.notification.UnsupportedContainerDriverRequest
import io.vamp.model.artifact.{ Gateway, Lookup }

import scala.concurrent.Future
import scala.io.Source
import scala.language.postfixOps
import scala.util.Try

object KubernetesDriverActor {

  object Schema extends Enumeration {
    val Docker = Value
  }

  private val configuration = ConfigFactory.load().getConfig("vamp.container-driver.kubernetes")

  val url = configuration.getString("url")

  val token = configuration.getString("token")

  val serviceType = KubernetesServiceType.withName(configuration.getString("service-type"))

  val createServices = configuration.getBoolean("create-services")

  val vampGatewayAgentId = configuration.getString("vamp-gateway-agent-id")
}

case class KubernetesDriverInfo(version: Any, paths: Any, api: Any, apis: Any)

class KubernetesDriverActor extends ContainerDriverActor with KubernetesContainerDriver with KubernetesDeployment with KubernetesService with KubernetesDaemonSet {

  import KubernetesDriverActor._

  protected val schema = KubernetesDriverActor.Schema

  protected val apiUrl = KubernetesDriverActor.url

  protected val apiHeaders = {
    Try(Source.fromFile(token).mkString).map {
      case bearer ⇒ ("Authorization" -> s"Bearer $bearer") :: RestClient.jsonHeaders
    } getOrElse RestClient.jsonHeaders
  }

  private val gatewayService = Map("vamp" -> "gateway")

  private val daemonService = Map("vamp" -> "daemon")

  def receive = {
    case InfoRequest                ⇒ reply(info)
    case Get(services)              ⇒ get(services)
    case d: Deploy                  ⇒ reply(deploy(d.deployment, d.cluster, d.service, d.update))
    case u: Undeploy                ⇒ reply(undeploy(u.deployment, u.service))
    case DeployedGateways(gateways) ⇒ reply(deployedGateways(gateways))
    case ds: DaemonSet              ⇒ reply(daemonSet(ds))
    case d: DeployDockerApp         ⇒ reply(deploy(d.app, d.update))
    case u: UndeployDockerApp       ⇒ reply(undeploy(u.app))
    case r: RetrieveDockerApp       ⇒ reply(retrieve(r.app))
    case any                        ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }

  private def info: Future[Any] = for {
    paths ← RestClient.get[Any](s"$apiUrl", apiHeaders) map {
      case map: Map[_, _] ⇒ map.headOption.map { case (_, value) ⇒ value }
      case any            ⇒ any
    }
    api ← RestClient.get[Any](s"$apiUrl/api", apiHeaders)
    apis ← RestClient.get[Any](s"$apiUrl/apis", apiHeaders)
    version ← RestClient.get[Any](s"$apiUrl/version", apiHeaders)
  } yield {
    ContainerInfo("kubernetes", KubernetesDriverInfo(version, paths, api, apis))
  }

  protected def get(deploymentServices: List[DeploymentServices]) = {
    val replyTo = sender()
    allContainerServices(deploymentServices).map(_.foreach {
      replyTo ! _
    })
  }

  protected override def deployedGateways(gateways: List[Gateway]) = {
    if (createServices) {
      services(gatewayService).map { response ⇒

        // update service ports
        gateways.filter {
          _.service.isEmpty
        } foreach { gateway ⇒
          response.items.find {
            item ⇒ item.metadata.labels.getOrElse(Lookup.entry, "") == gateway.lookupName
          } flatMap {
            item ⇒ item.spec.clusterIP.flatMap(ip ⇒ item.spec.ports.find(port ⇒ port.port == gateway.port.number).map(port ⇒ ip -> port))
          } foreach {
            case (ip, port) ⇒ setGatewayService(gateway, ip, port.nodePort)
          }
        }

        val items = response.items.flatMap { item ⇒ item.metadata.labels.get(Lookup.entry).map(_ -> item.metadata.name) } toMap

        // delete services
        val deleted = items.filter { case (l, _) ⇒ !gateways.exists(_.lookupName == l) } map { case (_, id) ⇒ deleteServiceById(id) }

        // create services
        val created = gateways.filter {
          case gateway ⇒ !items.exists { case (l, _) ⇒ l == gateway.lookupName }
        } map { gateway ⇒
          val ports = KubernetesServicePort("port", "TCP", gateway.port.number, gateway.port.number) :: Nil
          createService(gateway.name, serviceType, vampGatewayAgentId, ports, update = false, gatewayService ++ Map(Lookup.entry -> gateway.lookupName))
        }

        Future.sequence(created ++ deleted)
      }
    } else Future.successful(true)
  }

  private def daemonSet(ds: DaemonSet) = createDaemonSet(ds).flatMap { response ⇒
    ds.serviceType.map {
      case st ⇒
        val ports = ds.docker.portMappings.map { pm ⇒
          KubernetesServicePort(s"p${pm.containerPort}", pm.protocol.toUpperCase, pm.hostPort, pm.containerPort)
        }
        createService(ds.name, st, ds.name, ports, update = false, daemonService)
    } getOrElse Future.successful(response)
  }
}
