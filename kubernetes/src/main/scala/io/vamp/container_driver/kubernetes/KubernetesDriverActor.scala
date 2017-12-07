package io.vamp.container_driver.kubernetes

import akka.actor.{ Actor, ActorRef }
import io.vamp.common._
import io.vamp.common.http.HttpClient
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.ContainerDriverActor._
import io.vamp.container_driver._
import io.vamp.container_driver.notification.UnsupportedContainerDriverRequest
import io.vamp.model.artifact.{ Gateway, Workflow }

import scala.concurrent.Future
import scala.io.Source
import scala.language.postfixOps
import scala.util.Try

class KubernetesDriverActorMapper extends ClassMapper {
  val name = "kubernetes"
  val clazz = classOf[KubernetesDriverActor]
}

object KubernetesDriverActor {

  object Schema extends Enumeration {
    val Docker: Schema.Value = Value
  }

  private val config = "vamp.container-driver.kubernetes"

  val url: ConfigMagnet[String] = Config.string(s"$config.url")

  val workflowNamePrefix: ConfigMagnet[String] = Config.string(s"$config.workflow-name-prefix")

  val token: ConfigMagnet[String] = Config.string(s"$config.token")

  val bearer: ConfigMagnet[String] = Config.string(s"$config.bearer")

  val createServices: ConfigMagnet[Boolean] = Config.boolean(s"$config.create-services")

  val vampGatewayAgentId: ConfigMagnet[String] = Config.string(s"$config.vamp-gateway-agent-id")

  def serviceType()(implicit namespace: Namespace): KubernetesServiceType.Value = KubernetesServiceType.withName(Config.string(s"$config.service-type")())
}

case class KubernetesDriverInfo(version: Any, paths: Any, api: Any, apis: Any)

class KubernetesDriverActor
    extends ContainerDriverActor
    with KubernetesContainerDriver
    with KubernetesDeployment
    with KubernetesService
    with KubernetesJob
    with KubernetesDaemonSet {

  import KubernetesDriverActor._

  protected val schema: Enumeration = KubernetesDriverActor.Schema

  protected val apiUrl = KubernetesDriverActor.url()

  protected val apiHeaders: List[(String, String)] = {
    def headers(bearer: String) = ("Authorization" → s"Bearer $bearer") :: HttpClient.jsonHeaders

    if (bearer().nonEmpty) headers(bearer())
    else Try(Source.fromFile(token()).mkString).map(headers).getOrElse(HttpClient.jsonHeaders)
  }

  private val gatewayService = Map(ContainerDriver.labelNamespace() → "gateway")

  private val daemonService = Map(ContainerDriver.labelNamespace() → "daemon")

  protected val workflowNamePrefix = KubernetesDriverActor.workflowNamePrefix()

  def receive: Actor.Receive = {

    case InfoRequest                    ⇒ reply(info)

    case Get(services)                  ⇒ get(services)
    case d: Deploy                      ⇒ reply(deploy(d.deployment, d.cluster, d.service, d.update))
    case u: Undeploy                    ⇒ reply(undeploy(u.deployment, u.service))
    case DeployedGateways(gateways)     ⇒ reply(deployedGateways(gateways))

    case GetWorkflow(workflow, replyTo) ⇒ get(workflow, replyTo)
    case d: DeployWorkflow              ⇒ reply(deploy(d.workflow, d.update))
    case u: UndeployWorkflow            ⇒ reply(undeploy(u.workflow))

    case ds: DaemonSet                  ⇒ reply(daemonSet(ds))
    case job: Job                       ⇒ reply(createJob(job))

    case any                            ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }

  private def info: Future[Any] = for {
    paths ← httpClient.get[Any](s"$apiUrl", apiHeaders) map {
      case map: Map[_, _] ⇒ map.headOption.map { case (_, value) ⇒ value }
      case any            ⇒ any
    }
    api ← httpClient.get[Any](s"$apiUrl/api", apiHeaders)
    apis ← httpClient.get[Any](s"$apiUrl/apis", apiHeaders)
    version ← httpClient.get[Any](s"$apiUrl/version", apiHeaders)
  } yield {
    ContainerInfo("kubernetes", KubernetesDriverInfo(version, paths, api, apis))
  }

  protected def get(deploymentServices: List[DeploymentServices]): Unit = {
    val replyTo = sender()
    allContainerServices(deploymentServices).map(_.foreach {
      replyTo ! _
    })
  }

  protected def get(workflow: Workflow, replyTo: ActorRef): Unit = containerWorkflow(workflow).map(replyTo ! _)

  protected override def deployedGateways(gateways: List[Gateway]): Future[Any] = {
    if (createServices()) {
      services(gatewayService).map { response ⇒
        // update service ports
        gateways.filter {
          _.service.isEmpty
        } foreach { gateway ⇒
          response.items.find {
            item ⇒ item.metadata.labels.getOrElse(ContainerDriver.withNamespace(Lookup.entry), "") == gateway.lookupName
          } flatMap {
            item ⇒ item.spec.clusterIP.flatMap(ip ⇒ item.spec.ports.find(port ⇒ port.port == gateway.port.number).map(port ⇒ ip → port))
          } foreach {
            case (ip, port) ⇒ setGatewayService(gateway, ip, port.nodePort)
          }
        }

        val items = response.items.flatMap { item ⇒ item.metadata.labels.get(ContainerDriver.withNamespace(Lookup.entry)).map(_ → item.metadata.name) } toMap

        // delete services
        val deleted = items.filter { case (l, _) ⇒ !gateways.exists(_.lookupName == l) } map { case (_, id) ⇒ deleteServiceById(id) }

        // create services
        val created = gateways.filter {
          gateway ⇒ !items.exists { case (l, _) ⇒ l == gateway.lookupName }
        } map { gateway ⇒
          val ports = KubernetesServicePort("port", "TCP", gateway.port.number, gateway.port.number) :: Nil
          createService(gateway.name, serviceType(), vampGatewayAgentId(), ports, update = false, gatewayService ++ Map(ContainerDriver.withNamespace("gateway") → gateway.name, ContainerDriver.withNamespace(Lookup.entry) → gateway.lookupName))
        }

        Future.sequence(created ++ deleted)
      }
    }
    else Future.successful(true)
  }

  private def daemonSet(ds: DaemonSet) = createDaemonSet(ds).flatMap { response ⇒
    ds.serviceType.map { st ⇒
      val ports = ds.docker.portMappings.map { pm ⇒
        KubernetesServicePort(s"p${pm.containerPort}", pm.protocol.toUpperCase, pm.hostPort.getOrElse(0), pm.containerPort)
      }
      createService(ds.name, st, ds.name, ports, update = false, daemonService ++ Map(ContainerDriver.withNamespace("daemon") → ds.name))
    } getOrElse Future.successful(response)
  }
}
