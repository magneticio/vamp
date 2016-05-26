package io.vamp.container_driver.kubernetes

import com.typesafe.config.ConfigFactory
import io.vamp.common.http.RestClient
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.ContainerDriverActor._
import io.vamp.container_driver._
import io.vamp.container_driver.notification.UnsupportedContainerDriverRequest
import io.vamp.model.artifact.Gateway

import scala.concurrent.Future
import scala.language.postfixOps

object KubernetesDriverActor {

  object Schema extends Enumeration {
    val Docker = Value
  }

  private val configuration = ConfigFactory.load().getConfig("vamp.container-driver.kubernetes")

  val kubernetesUrl = configuration.getString("url")

  val createServices = configuration.getBoolean("create-services")

  val vampGatewayAgentId = configuration.getString("vamp-gateway-agent-id")
}

case class KubernetesDriverInfo(version: Any, paths: Any, api: Any, apis: Any)

class KubernetesDriverActor extends ContainerDriverActor with KubernetesContainerDriver with KubernetesDeployment with KubernetesService with KubernetesDaemonSet {

  import KubernetesDriverActor._

  protected val schema = KubernetesDriverActor.Schema

  protected val kubernetesUrl = KubernetesDriverActor.kubernetesUrl

  private val gatewayService = Map("vamp" -> "gateway")

  private val daemonService = Map("vamp" -> "daemon")

  def receive = {
    case InfoRequest                ⇒ reply(info)
    case All                        ⇒ reply(allContainerServices)
    case d: Deploy                  ⇒ reply(deploy(d.deployment, d.cluster, d.service, d.update))
    case u: Undeploy                ⇒ reply(undeploy(u.deployment, u.service))
    case DeployedGateways(gateways) ⇒ reply(deployedGateways(gateways))
    case ds: DaemonSet              ⇒ reply(daemonSet(ds))
    case any                        ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }

  private def info: Future[Any] = for {
    paths ← RestClient.get[Any](s"$kubernetesUrl") map {
      case map: Map[_, _] ⇒ map.headOption.map { case (_, value) ⇒ value }
      case any            ⇒ any
    }
    api ← RestClient.get[Any](s"$kubernetesUrl/api")
    apis ← RestClient.get[Any](s"$kubernetesUrl/apis")
    version ← RestClient.get[Any](s"$kubernetesUrl/version")
  } yield {
    ContainerInfo("kubernetes", KubernetesDriverInfo(version, paths, api, apis))
  }

  protected override def deployedGateways(gateways: List[Gateway]) = {
    if (createServices) {
      services(gatewayService).map { response ⇒

        // update service ports
        gateways.filter { _.servicePort.isEmpty } foreach { gateway ⇒
          response.items.find {
            item ⇒ item.metadata.labels.getOrElse("lookup_name", "") == gateway.lookupName
          } flatMap {
            item ⇒ item.spec.ports.find(port ⇒ port.port == gateway.port.number)
          } foreach {
            port ⇒ setServicePort(gateway, port.nodePort)
          }
        }

        val items = response.items.flatMap { item ⇒ item.metadata.labels.get("lookup_name").map(_ -> item.metadata.name) } toMap

        // delete services
        val deleted = items.filter { case (l, _) ⇒ !gateways.exists(_.lookupName == l) } map { case (_, id) ⇒ deleteServiceById(id) }

        // create services
        val created = gateways.filter {
          case gateway ⇒ !items.exists { case (l, _) ⇒ l == gateway.lookupName }
        } map { gateway ⇒
          val ports = KubernetesServicePort("port", "TCP", gateway.port.number, gateway.port.number) :: Nil
          createService(gateway.name, vampGatewayAgentId, ports, update = false, gatewayService ++ Map("lookup_name" -> gateway.lookupName))
        }

        Future.sequence(created ++ deleted)
      }
    } else Future.successful(true)
  }

  private def daemonSet(ds: DaemonSet) = createDaemonSet(ds).flatMap { _ ⇒
    val ports = ds.docker.portMappings.map { pm ⇒
      KubernetesServicePort(s"p${pm.containerPort}", pm.protocol.toUpperCase, pm.hostPort, pm.containerPort)
    }
    createService(ds.name, ds.name, ports, update = false, daemonService)
  }
}
