package io.vamp.container_driver.kubernetes

import akka.actor.{ Actor, ActorRef }
import io.kubernetes.client.ApiException
import io.vamp.common._
import io.vamp.common.util.{ HashUtil, YamlUtil }
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.ContainerDriverActor._
import io.vamp.container_driver._
import io.vamp.container_driver.notification.UnsupportedContainerDriverRequest
import io.vamp.model.artifact.{ Gateway, Workflow }
import io.vamp.model.reader.{ MegaByte, Quantity }
import io.vamp.model.resolver.NamespaceValueResolver
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

  case class UnDeployKubernetesItems(request: String)

  case class CreateJob(job: Job)

  case class DeleteJob(selector: String)

}

class KubernetesDriverActor
    extends ContainerDriverActor
    with KubernetesContainerDriver
    with KubernetesDeployment
    with KubernetesService
    with KubernetesJob
    with KubernetesNode
    with KubernetesDaemonSet
    with KubernetesNamespace
    with NamespaceValueResolver {

  import KubernetesDriverActor._

  protected val schema: Enumeration = KubernetesDriverActor.Schema

  private lazy val k8sConfig = K8sClientConfig()

  protected lazy val k8sClient: K8sClient = K8sClient.acquire(k8sConfig)

  private val gatewayService = Map(ContainerDriver.labelNamespace() → "gateway")

  private val daemonService = Map(ContainerDriver.labelNamespace() → "daemon")

  private val vampGatewayAgentId = resolveWithOptionalNamespace(KubernetesDriverActor.vampGatewayAgentId())._1

  protected val workflowNamePrefix = KubernetesDriverActor.workflowNamePrefix()

  def receive: Actor.Receive = {

    case InfoRequest                    ⇒ reply(Future(info()))

    case GetNodes                       ⇒ reply(Future(schedulerNodes))
    case GetRoutingGroups               ⇒ reply(Future(routingGroups))

    case Get(services, equality)        ⇒ get(services, equality)
    case d: Deploy                      ⇒ reply(deploy(d.deployment, d.cluster, d.service, d.update))
    case u: Undeploy                    ⇒ reply(undeploy(u.deployment, u.service))
    case DeployedGateways(gateways)     ⇒ reply(updateGateways(gateways))

    case GetWorkflow(workflow, replyTo) ⇒ get(workflow, replyTo)
    case d: DeployWorkflow              ⇒ reply(deploy(d.workflow, d.update))
    case u: UndeployWorkflow            ⇒ reply(undeploy(u.workflow))

    case ds: DaemonSet                  ⇒ reply(daemonSet(ds))
    case CreateJob(job)                 ⇒ reply(createJob(job))
    case DeleteJob(job)                 ⇒ reply(deleteJob(job))
    case ns: CreateNamespace            ⇒ reply(createNamespace(ns))

    case d: DeployKubernetesItems       ⇒ reply(deploy(d.request))
    case u: UnDeployKubernetesItems     ⇒ reply(undeploy(u.request))
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

  override def postStop(): Unit = K8sClient.release(k8sConfig)

  private def info() = ContainerInfo(
    "kubernetes",
    Map(
      "url" → k8sClient.config.url,
      "groups" → Try(k8sClient.apisApi.getAPIVersions.getGroups.asScala).toOption.getOrElse(Nil).map(_.getName)
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
      items.filter { case (l, _) ⇒ !gateways.exists(_.lookupName == l) } foreach { case (_, name) ⇒ deleteService(name) }

      // create services
      gateways.filter {
        gateway ⇒ !items.exists { case (l, _) ⇒ l == gateway.lookupName }
      } foreach { gateway ⇒
        val ports = KubernetesServicePort("port", "TCP", gateway.port.number) :: Nil
        createService(gateway.name, serviceType(), vampGatewayAgentId, ports, update = false, gatewayService ++ Map(ContainerDriver.withNamespace("gateway") → gateway.name, ContainerDriver.withNamespace(Lookup.entry) → gateway.lookupName))
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
    def process(any: Any): Unit = {
      val kind = any.asInstanceOf[Map[String, String]]("kind")
      val request = write(any.asInstanceOf[AnyRef])(DefaultFormats)
      kind match {
        case "Service"    ⇒ createService(request)
        case "DaemonSet"  ⇒ createDaemonSet(request)
        case "Deployment" ⇒ createDeployment(request)
        case other        ⇒ log.warning(s"Cannot process kind: $other")
      }
    }

    YamlUtil.convert(YamlUtil.yaml.loadAll(request), preserveOrder = false) match {
      case l: List[_] ⇒ l.foreach(process)
      case other      ⇒ process(other)
    }
  }

  private def undeploy(request: String): Unit = {
    def process(any: Any): Unit = {
      val kind = any.asInstanceOf[Map[String, String]]("kind")
      val name = any.asInstanceOf[Map[String, Map[String, String]]]("metadata")("name")
      kind match {
        case "Service"    ⇒ deleteService(name)
        case "DaemonSet"  ⇒ deleteDaemonSet(name)
        case "Deployment" ⇒ deleteDeployment(name)
        case other        ⇒ log.warning(s"Cannot process kind: $other")
      }
    }

    YamlUtil.convert(YamlUtil.yaml.loadAll(request), preserveOrder = false) match {
      case l: List[_] ⇒ l.foreach(process)
      case other      ⇒ process(other)
    }
  }

  private def schedulerNodes: List[SchedulerNode] = super[KubernetesNode].nodes.flatMap { node ⇒
    Try {
      val capacity = node.getStatus.getCapacity
      SchedulerNode(
        name = HashUtil.hexSha1(node.getMetadata.getName),
        capacity = SchedulerNodeSize(Quantity.of(capacity.getOrDefault("cpu", "0")), MegaByte.of(capacity.getOrDefault("memory", "0MB")))
      )
    }.toOption
  } toList

  private def routingGroups: List[RoutingGroup] = {
    val services = servicesForAllNamespaces().flatMap { service ⇒
      Try {
        RoutingGroup(
          name = service.getMetadata.getName,
          kind = "service",
          namespace = service.getMetadata.getNamespace,
          labels = service.getMetadata.getLabels.asScala.toMap,
          image = None,
          instances = List(
            RoutingInstance(
              ip = Option(service.getSpec.getClusterIP).get, // fail if null
              ports = service.getSpec.getPorts.asScala.map(p ⇒ RoutingInstancePort(p.getPort, p.getTargetPort.toInt)).toList
            )
          )
        )
      } map (_ :: Nil) getOrElse Nil
    } toList

    val pods = podsForAllNamespaces().flatMap { pod ⇒
      pod.getSpec.getContainers.asScala.flatMap { container ⇒
        Try {
          RoutingGroup(
            name = container.getName,
            kind = "container",
            namespace = pod.getMetadata.getNamespace,
            labels = pod.getMetadata.getLabels.asScala.toMap,
            image = Option(container.getImage),
            instances = List(
              RoutingInstance(
                ip = Option(pod.getStatus.getPodIP).get, // fail if null
                ports = container.getPorts.asScala.map(p ⇒ RoutingInstancePort(p.getContainerPort, p.getContainerPort)).toList
              )
            )
          )
        } map (_ :: Nil) getOrElse Nil
      }
    } toList

    services ++ pods
  }
}
