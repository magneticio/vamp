package io.vamp.container_driver.kubernetes

import akka.actor.{ Actor, ActorRef }
import io.kubernetes.client.ApiException
import io.vamp.common._
import io.vamp.common.util.YamlUtil
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.ContainerDriverActor._
import io.vamp.container_driver._
import io.vamp.container_driver.notification.UnsupportedContainerDriverRequest
import io.vamp.model.artifact.{ Gateway, Workflow }
import org.json4s.DefaultFormats
import org.json4s.native.Serialization.write

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

class KubernetesDriverActorMapper extends ClassMapper {
  val name = "kubernetes"
  val clazz: Class[_] = classOf[KubernetesDriverActor]
}

object KubernetesDriverActor {

  import KubernetesContainerDriver._

  object Schema extends Enumeration {
    val Docker: Schema.Value = Value
  }

  val workflowNamePrefix: ConfigMagnet[String] = Config.string(s"$config.workflow-name-prefix")

  val createServices: ConfigMagnet[Boolean] = Config.boolean(s"$config.create-services")

  val vampGatewayAgentId: ConfigMagnet[String] = Config.string(s"$config.vamp-gateway-agent-id")

  def serviceType()(implicit namespace: Namespace): KubernetesServiceType.Value = KubernetesServiceType.withName(Config.string(s"$config.service-type")())

  case class DeployKubernetesItems(request: String)

}

class KubernetesDriverActor
    extends ContainerDriverActor
    with KubernetesContainerDriver
    with KubernetesDeployment
    with KubernetesService
    with KubernetesJob
    with KubernetesDaemonSet
    with KubernetesNamespace {

  import KubernetesDriverActor._

  protected val schema: Enumeration = KubernetesDriverActor.Schema

  protected lazy val k8sClient: K8sClient = new K8sClient(K8sConfig(), log)

  private val gatewayService = Map(ContainerDriver.labelNamespace() → "gateway")

  private val daemonService = Map(ContainerDriver.labelNamespace() → "daemon")

  protected val workflowNamePrefix = KubernetesDriverActor.workflowNamePrefix()

  def receive: Actor.Receive = {

    case InfoRequest                    ⇒ reply(Future(info()))

    case Get(services, equality)        ⇒ get(services, equality)
    case d: Deploy                      ⇒ reply(deploy(d.deployment, d.cluster, d.service, d.update))
    case u: Undeploy                    ⇒ reply(undeploy(u.deployment, u.service))
    case DeployedGateways(gateways)     ⇒ reply(updateGateways(gateways))

    case GetWorkflow(workflow, replyTo) ⇒ get(workflow, replyTo)
    case d: DeployWorkflow              ⇒ reply(deploy(d.workflow, d.update))
    case u: UndeployWorkflow            ⇒ reply(undeploy(u.workflow))

    case ds: DaemonSet                  ⇒ reply(daemonSet(ds))
    case job: Job                       ⇒ reply(createJob(job))
    case ns: CreateNamespace            ⇒ reply(createNamespace(ns))

    case dm: DeployKubernetesItems      ⇒ reply(deploy(dm.request))
    case any                            ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }

  private def reply(fn: ⇒ Unit): Unit = reply {
    try Future.successful(fn)
    catch {
      case e: ApiException ⇒
        log.error(s"[${e.getCode}] - ${e.getResponseBody}")
        Future.failed(e)
      case e: Exception ⇒ Future.failed(e)
    }
  }

  override def postStop(): Unit = k8sClient.close()

  private def info() = ContainerInfo(
    "kubernetes",
    Map(
      "url" → k8sClient.config.url,
      "groups" → k8sClient.apisApi.getAPIVersions.getGroups.asScala.map(_.getName)
    )
  )

  protected def get(deploymentServices: List[DeploymentServices], equalityRequest: ServiceEqualityRequest): Unit = {
    val replyTo = sender()
    containerServices(deploymentServices, equalityRequest).foreach {
      replyTo ! _
    }
  }

  protected def get(workflow: Workflow, replyTo: ActorRef): Unit = replyTo ! containerWorkflow(workflow)

  private def updateGateways(gateways: List[Gateway]): Unit = {
    if (createServices()) {
      val v1Services = services(gatewayService)

      // update service ports
      gateways.filter {
        _.service.isEmpty
      } foreach { gateway ⇒

        v1Services.find {
          v1Service ⇒ v1Service.getMetadata.getLabels.asScala.getOrElse(ContainerDriver.withNamespace(Lookup.entry), "") == gateway.lookupName
        } flatMap {
          v1Service ⇒
            val ip = v1Service.getSpec.getClusterIP
            if (ip.nonEmpty)
              v1Service.getSpec.getPorts.asScala.find(port ⇒ port.getPort.toInt == gateway.port.number).map(port ⇒ ip → port)
            else None
        } foreach {
          case (ip, port) ⇒ setGatewayService(gateway, ip, port.getNodePort.toInt)
        }
      }

      val items = v1Services.flatMap { v1Service ⇒ v1Service.getMetadata.getLabels.asScala.get(ContainerDriver.withNamespace(Lookup.entry)).map(_ → v1Service.getMetadata.getName) } toMap

      // delete services
      items.filter { case (l, _) ⇒ !gateways.exists(_.lookupName == l) } foreach { case (_, id) ⇒ deleteServiceById(id) }

      // create services
      gateways.filter {
        gateway ⇒ !items.exists { case (l, _) ⇒ l == gateway.lookupName }
      } foreach { gateway ⇒
        val ports = KubernetesServicePort("port", "TCP", gateway.port.number) :: Nil
        createService(gateway.name, serviceType(), vampGatewayAgentId(), ports, update = false, gatewayService ++ Map(ContainerDriver.withNamespace("gateway") → gateway.name, ContainerDriver.withNamespace(Lookup.entry) → gateway.lookupName))
      }
    }
  }

  private def daemonSet(ds: DaemonSet): Unit = {
    createDaemonSet(ds)
    ds.serviceType.foreach { st ⇒
      val ports = ds.docker.portMappings.map { pm ⇒
        KubernetesServicePort(s"p${pm.containerPort}", pm.protocol.toUpperCase, pm.hostPort.getOrElse(pm.containerPort))
      }
      createService(ds.name, st, ds.name, ports, update = false, daemonService ++ Map(ContainerDriver.withNamespace("daemon") → ds.name))
    }
  }

  private def deploy(request: String): Unit = {
    def process(any: Any): Unit = Try {
      val kind = any.asInstanceOf[Map[String, String]]("kind")
      val request = write(any.asInstanceOf[AnyRef])(DefaultFormats)
      kind match {
        case "Service"   ⇒ createService(request)
        case "DaemonSet" ⇒ createDaemonSet(request)
        case other       ⇒ log.warning(s"Cannot process kind: $other")
      }
    }

    YamlUtil.convert(YamlUtil.yaml.loadAll(request), preserveOrder = false) match {
      case l: List[_] ⇒ l.foreach(process)
      case other      ⇒ process(other)
    }
  }
}
