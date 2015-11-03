package io.vamp.gateway_driver

import akka.pattern.ask
import io.vamp.common.akka.Bootstrap.{ Shutdown, Start }
import io.vamp.common.akka._
import io.vamp.common.crypto.Hash
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.gateway_driver.GatewayStore.{ Get, Put }
import io.vamp.gateway_driver.model._
import io.vamp.gateway_driver.notification.{ GatewayDriverNotificationProvider, GatewayDriverResponseError, UnsupportedGatewayDriverRequest }
import io.vamp.model.artifact._
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.Future
import scala.util.matching.Regex

object GatewayDriverActor {

  trait GatewayDriverMessage

  case class GetAllAfterPurge(deployments: List[Deployment]) extends GatewayDriverMessage

  case class Create(deployment: Deployment, cluster: DeploymentCluster, port: Port) extends GatewayDriverMessage

  case class CreateEndpoint(deployment: Deployment, port: Port) extends GatewayDriverMessage

  case class Remove(deployment: Deployment, cluster: DeploymentCluster, port: Port) extends GatewayDriverMessage

  case class RemoveEndpoint(deployment: Deployment, port: Port) extends GatewayDriverMessage

}

class GatewayDriverActor(marshaller: GatewayMarshaller) extends GatewayConverter with PulseFailureNotifier with CommonSupportForActors with GatewayDriverNotificationProvider {

  import io.vamp.gateway_driver.GatewayDriverActor._

  lazy implicit val timeout = GatewayStore.timeout

  def receive = {
    case Start                             ⇒
    case Shutdown                          ⇒
    case InfoRequest                       ⇒ reply(info)
    case GetAllAfterPurge(deployments)     ⇒ reply(getAllAfterPurge(deployments))
    case Create(deployment, cluster, port) ⇒ update(createGateway(deployment, cluster, port))
    case Remove(deployment, cluster, port) ⇒ remove(clusterGatewayName(deployment, cluster, port))
    case CreateEndpoint(deployment, port)  ⇒ update(createGateway(deployment, port))
    case RemoveEndpoint(deployment, port)  ⇒ remove(endpointGatewayName(deployment, port))
    case other                             ⇒ unsupported(UnsupportedGatewayDriverRequest(other))
  }

  override def errorNotificationClass = classOf[GatewayDriverResponseError]

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)

  private def info = IoC.actorFor[GatewayStore] ? InfoRequest map { case data ⇒ Map("store" -> data, "marshaller" -> marshaller.info) }

  private def getAllAfterPurge(deployments: List[Deployment]) = {
    log.debug(s"Read all gateways")
    read map {
      case gateways ⇒
        val purged = gateways.filter {
          case gateway ⇒ deployments.exists(deployment ⇒ gateway.name.substring(0, 8) == deployment.name.substring(0, 8))
        }
        if (gateways.size != purged.size) persist(purged)
        toDeploymentGateways(purged)
    }
  }

  private def read: Future[List[Gateway]] = IoC.actorFor[GatewayStore] ? Get map {
    case gateways ⇒ gateways.asInstanceOf[List[Gateway]]
  }

  private def update(gateway: Gateway) = {
    log.info(s"Update gateway: ${gateway.name}")
    read map { case gateways ⇒ persist(gateway :: gateways.filterNot(_.name == gateway.name)) }
  }

  private def remove(name: String) = {
    log.info(s"Remove gateway: $name")
    read map { case gateways ⇒ persist(gateways.filterNot(_.name == name)) }
  }

  private def persist(gateways: List[Gateway]) = IoC.actorFor[GatewayStore] ! Put(gateways, Option(marshaller.marshall(gateways)))
}

trait GatewayConverter extends GatewayDriverNameMatcher {

  def toDeploymentGateways(gateways: List[Gateway]): DeploymentGateways = {
    val clusterGateways = gateways.filter(gateway ⇒ processableClusterGateway(gateway.name)).map(gateway ⇒ ClusterGateway(clusterGatewayNameMatcher(gateway.name), gateway.port, services(gateway, gateway.services)))
    val endpointGateways = gateways.filter(gateway ⇒ processableEndpointGateway(gateway.name)).map(gateway ⇒ EndpointGateway(endpointGatewayNameMatcher(gateway.name), gateway.port, services(gateway, gateway.services)))

    DeploymentGateways(clusterGateways, endpointGateways)
  }

  def createGateway(deployment: Deployment, cluster: DeploymentCluster, port: Port) = gateway(clusterGatewayName(deployment, cluster, port), deployment, Some(cluster), port)

  def createGateway(deployment: Deployment, port: Port) = gateway(endpointGatewayName(deployment, port), deployment, None, port)

  private def gateway(name: String, deployment: Deployment, cluster: Option[DeploymentCluster], port: Port) = {
    def `type`(port: Port): String = if (port.`type` == Port.Http) "http" else "tcp"
    cluster match {
      case None    ⇒ Gateway(name, port.number, `type`(port), filters(cluster), services(deployment, None, port))
      case Some(c) ⇒ Gateway(name, c.routes.get(port.number).get, `type`(port), filters(cluster), services(deployment, cluster, port))
    }
  }

  private def filters(cluster: Option[DeploymentCluster]): List[model.Filter] = {
    (cluster match {
      case None ⇒ None
      case Some(c) ⇒ c.services.flatMap { service ⇒
        service.routing.getOrElse(DefaultRouting("", None, Nil)).filters.flatMap({
          case filter: DefaultFilter ⇒ model.Filter(if (filter.name.isEmpty) None else Option(filter.name), filter.condition, s"${artifactName2Id(service.breed, serviceIdMatcher)}") :: Nil
          case _                     ⇒ Nil
        })
      }
    }) match {
      case result: List[_] ⇒ result.asInstanceOf[List[model.Filter]]
      case _               ⇒ Nil
    }
  }

  private def services(deployment: Deployment, cluster: Option[DeploymentCluster], port: Port): List[model.Service] = cluster match {
    case Some(c) ⇒
      c.services.map { service ⇒ model.Service(s"${artifactName2Id(service.breed, serviceIdMatcher)}", service.routing.getOrElse(DefaultRouting("", Some(100), Nil)).weight.getOrElse(100), service.servers.map(server(service, _, port))) }

    case None ⇒
      val name = TraitReference.referenceFor(port.name).map(_.referenceWithoutGroup).getOrElse(port.name)
      model.Service(s"${string2Id(name, serviceIdMatcher)}", 100, servers(deployment, port)) :: Nil
  }

  private def server(service: DeploymentService, server: DeploymentServer, port: Port) = server.ports.get(port.number) match {
    case Some(p) ⇒ Server(artifactName2Id(server), server.host, p)
    case _       ⇒ model.Server(artifactName2Id(server), server.host, 0)
  }

  private def servers(deployment: Deployment, port: Port): List[model.Server] = {
    TraitReference.referenceFor(port.name) match {
      case Some(TraitReference(cluster, _, name)) ⇒
        (for {
          h ← deployment.hosts.find(host ⇒ TraitReference.referenceFor(host.name) match {
            case Some(TraitReference(c, _, _)) if c == cluster ⇒ true
            case _ ⇒ false
          })
          p ← deployment.ports.find(_.name == port.name)
        } yield (h, p) match {
          case (host, gatewayPort) ⇒
            deployment.clusters.find(_.name == cluster) match {
              case None ⇒ Nil
              case Some(c) ⇒
                c.routes.values.find(_ == gatewayPort.number) match {
                  case Some(_) ⇒ model.Server(string2Id(s"${deployment.name}_${port.number}"), host.value.get, gatewayPort.number) :: Nil
                  case _       ⇒ Nil
                }
            }
          case _ ⇒ Nil
        }) getOrElse Nil
      case _ ⇒ Nil
    }
  }

  private def services(gateway: Gateway, services: List[model.Service]): List[GatewayService] = services.map { service ⇒
    GatewayService(serviceGatewayNameMatcher(service.name), service.weight, service.servers, gateway.filters.filter(_.destination == service.name))
  }

  private def processableClusterGateway(name: String): Boolean = name.split(nameDelimiter).size == 3

  private def processableEndpointGateway(name: String): Boolean = name.split(nameDelimiter).size == 2
}

trait GatewayDriverNameMatcher {

  val nameDelimiter = "_"

  val idMatcher = """^[a-zA-Z0-9][a-zA-Z0-9.\-_]{3,63}$""".r
  val serviceIdMatcher = """^[a-zA-Z0-9][a-zA-Z0-9.\-_:]{1,63}$""".r

  def clusterGatewayNameMatcher(id: String): (Deployment, DeploymentCluster, Port) ⇒ Boolean = { (deployment: Deployment, cluster: DeploymentCluster, port: Port) ⇒ id == clusterGatewayName(deployment, cluster, port) }

  def serviceGatewayNameMatcher(id: String): (DeploymentService) ⇒ Boolean = { (deploymentService: DeploymentService) ⇒ id == artifactName2Id(deploymentService.breed, serviceIdMatcher) }

  def endpointGatewayNameMatcher(id: String): (Deployment, Option[Port]) ⇒ Boolean = (deployment: Deployment, optionalPort: Option[Port]) ⇒ optionalPort match {
    case None       ⇒ isDeploymentEndpoint(id, deployment)
    case Some(port) ⇒ id == endpointGatewayName(deployment, port)
  }

  def clusterGatewayName(deployment: Deployment, cluster: DeploymentCluster, port: Port): String = {
    val id = s"${deployment.name}$nameDelimiter${cluster.name}$nameDelimiter${port.number}"
    val hashId = string2Id(id)

    if (hashId == id) id else s"${id.substring(0, 8)}$nameDelimiter${id.substring(9, 13)}$nameDelimiter$hashId"
  }

  def endpointGatewayName(deployment: Deployment, port: Port): String =
    s"${artifactName2Id(deployment)}$nameDelimiter${port.number}"

  def isDeploymentEndpoint(id: String, deployment: Deployment): Boolean =
    id.startsWith(s"${artifactName2Id(deployment)}$nameDelimiter")

  def artifactName2Id(artifact: Artifact, matcher: Regex = idMatcher) = string2Id(artifact.name, matcher)

  def string2Id(string: String, matcher: Regex = idMatcher) = string match {
    case matcher(_*) ⇒ string
    case _           ⇒ Hash.hexSha1(string)
  }
}