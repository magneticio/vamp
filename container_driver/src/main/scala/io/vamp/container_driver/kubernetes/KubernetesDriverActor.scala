package io.vamp.container_driver.kubernetes

import com.typesafe.config.ConfigFactory
import io.vamp.common.http.RestClient
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.ContainerDriverActor._
import io.vamp.container_driver._
import io.vamp.container_driver.notification.UnsupportedContainerDriverRequest
import io.vamp.model.artifact.Gateway

import scala.concurrent.Future

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

  def receive = {
    case InfoRequest              ⇒ reply(info)
    case All                      ⇒ reply(allContainerServices)
    case d: Deploy                ⇒ reply(deploy(d.deployment, d.cluster, d.service, d.update))
    case u: Undeploy              ⇒ reply(undeploy(u.deployment, u.service))
    case DeployGateway(gateway)   ⇒ reply(deployGateway(gateway))
    case UndeployGateway(gateway) ⇒ reply(undeployGateway(gateway))
    case ds: DaemonSet            ⇒ reply(daemonSet(ds))
    case any                      ⇒ unsupported(UnsupportedContainerDriverRequest(any))
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

  private def deployGateway(gateway: Gateway) = {
    if (createServices) {
      val ports = KubernetesServicePort("port", "TCP", gateway.port.number, gateway.port.number) :: Nil
      createService(gateway.name, vampGatewayAgentId, ports, update = true)
    } else Future.successful(true)
  }

  private def undeployGateway(gateway: Gateway) = {
    if (createServices) deleteService(gateway.name) else Future.successful(true)
  }

  private def daemonSet(ds: DaemonSet) = createDaemonSet(ds).flatMap { _ ⇒
    val ports = ds.docker.portMappings.map { pm ⇒
      KubernetesServicePort(s"p${pm.containerPort}", pm.protocol.toUpperCase, pm.hostPort, pm.containerPort)
    }
    createService(ds.name, ds.name, ports, update = false)
  }
}
