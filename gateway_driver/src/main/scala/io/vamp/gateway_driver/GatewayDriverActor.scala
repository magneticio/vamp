package io.vamp.gateway_driver

import akka.pattern.ask
import io.vamp.common.akka.Bootstrap.{ Shutdown, Start }
import io.vamp.common.akka._
import io.vamp.common.crypto.Hash
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.gateway_driver.GatewayStore.{ Get, Put }
import io.vamp.gateway_driver.kibana.KibanaDashboardActor
import io.vamp.gateway_driver.model.{ Gateway ⇒ DriverGateway, ClusterGateway ⇒ DriverClusterGateway, _ }
import io.vamp.gateway_driver.notification.{ GatewayDriverNotificationProvider, GatewayDriverResponseError, UnsupportedGatewayDriverRequest }
import io.vamp.model.artifact._
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.Future
import scala.language.postfixOps
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

  private def info = (for {
    store ← IoC.actorFor[GatewayStore] ? InfoRequest
    kibana ← IoC.actorFor[KibanaDashboardActor] ? InfoRequest
  } yield (store, kibana)).map {
    case (store, kibana) ⇒ Map("store" -> store, "marshaller" -> marshaller.info, "kibana" -> kibana)
  }

  private def getAllAfterPurge(deployments: List[Deployment]) = {
    log.debug(s"Read all gateways")

    val gatewayNames = deployments.flatMap { deployment ⇒
      deployment.clusters.flatMap { cluster ⇒
        cluster.services.flatMap { service ⇒
          service.breed.ports.map { port ⇒
            clusterGatewayName(deployment, cluster, port)
          }
        }
      } ++ deployment.endpoints.map(endpointGatewayName(deployment, _))
    } toSet

    read map {
      case gateways ⇒
        val purged = gateways.filter(gateway ⇒ gatewayNames.contains(gateway.name))
        if (gateways.size != purged.size) persist(purged)
        toDeploymentGateways(purged)
    }
  }

  private def read: Future[List[DriverGateway]] = IoC.actorFor[GatewayStore] ? Get map {
    case gateways ⇒ gateways.asInstanceOf[List[DriverGateway]]
  }

  private def update(gateway: DriverGateway) = {
    log.info(s"Update gateway: ${gateway.name}")
    read map { case gateways ⇒ persist(gateway :: gateways.filterNot(_.name == gateway.name)) }
  }

  private def remove(name: String) = {
    log.info(s"Remove gateway: $name")
    read map { case gateways ⇒ persist(gateways.filterNot(_.name == name)) }
  }

  private def persist(gateways: List[DriverGateway]) = IoC.actorFor[GatewayStore] ! Put(gateways, Option(marshaller.marshall(gateways)))
}

trait GatewayConverter extends GatewayDriverNameMatcher {

  def toDeploymentGateways(gateways: List[DriverGateway]): DeploymentGateways = {
    val clusterGateways = gateways.filter(gateway ⇒ processableClusterGateway(gateway.name)).map(gateway ⇒ DriverClusterGateway(clusterGatewayNameMatcher(gateway.name), gateway.port, services(gateway, gateway.services)))
    val endpointGateways = gateways.filter(gateway ⇒ processableEndpointGateway(gateway.name)).map(gateway ⇒ EndpointGateway(endpointGatewayNameMatcher(gateway.name), gateway.port, services(gateway, gateway.services)))

    DeploymentGateways(clusterGateways, endpointGateways)
  }

  def createGateway(deployment: Deployment, cluster: DeploymentCluster, port: Port) = gateway(clusterGatewayName(deployment, cluster, port), deployment, Some(cluster), port)

  def createGateway(deployment: Deployment, port: Port) = gateway(endpointGatewayName(deployment, port), deployment, None, port)

  private def gateway(name: String, deployment: Deployment, cluster: Option[DeploymentCluster], port: Port) = {
    def `type`(port: Port): String = if (port.`type` == Port.Http) "http" else "tcp"
    cluster match {
      case None    ⇒ DriverGateway(name, port.number, `type`(port), filters(cluster, port), services(deployment, None, port), None)
      case Some(c) ⇒ DriverGateway(name, c.portMapping.get(port.number).get, `type`(port), filters(cluster, port), services(deployment, cluster, port), c.routingBy(port.name).flatMap(_.sticky))
    }
  }

  private def filters(cluster: Option[DeploymentCluster], port: Port): List[model.Filter] = {
    (cluster match {
      case None ⇒ None
      case Some(c) ⇒ c.services.flatMap { service ⇒
        route(c, service, port).filters.flatMap({
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
      c.services.map { service ⇒ model.Service(s"${artifactName2Id(service.breed, serviceIdMatcher)}", route(c, service, port).weight.getOrElse(100), service.instances.map(server(service, _, port))) }

    case None ⇒
      val name = TraitReference.referenceFor(port.name).map(_.referenceWithoutGroup).getOrElse(port.name)
      model.Service(s"${string2Id(name, serviceIdMatcher)}", 100, servers(deployment, port)) :: Nil
  }

  private def route(cluster: DeploymentCluster, service: DeploymentService, port: Port): DefaultRoute = {
    cluster.route(service, port.name).getOrElse(DefaultRoute("", "", None, Nil))
  }

  private def server(service: DeploymentService, server: DeploymentInstance, port: Port) = server.ports.get(port.number) match {
    case Some(p) ⇒ Instance(artifactName2Id(server), server.host, p)
    case _       ⇒ model.Instance(artifactName2Id(server), server.host, 0)
  }

  private def servers(deployment: Deployment, port: Port): List[model.Instance] = {
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
                c.portMapping.values.find(_ == gatewayPort.number) match {
                  case Some(_) ⇒ model.Instance(string2Id(s"${deployment.name}_${port.name}"), host.value.get, gatewayPort.number) :: Nil
                  case _       ⇒ Nil
                }
            }
          case _ ⇒ Nil
        }) getOrElse Nil
      case _ ⇒ Nil
    }
  }

  private def services(gateway: DriverGateway, services: List[model.Service]): List[GatewayService] = services.map { service ⇒
    GatewayService(serviceGatewayNameMatcher(service.name), service.weight, service.instances, gateway.filters.filter(_.destination == service.name))
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

  def clusterGatewayName(deployment: Deployment, cluster: DeploymentCluster, port: Port): String =
    string2Id(s"${deployment.name}$nameDelimiter${cluster.name}$nameDelimiter${port.name}")

  def endpointGatewayName(deployment: Deployment, port: Port): String =
    s"${artifactName2Id(deployment)}$nameDelimiter${port.name}"

  def isDeploymentEndpoint(id: String, deployment: Deployment): Boolean =
    id.startsWith(s"${artifactName2Id(deployment)}$nameDelimiter")

  def artifactName2Id(artifact: Artifact, matcher: Regex = idMatcher) = string2Id(artifact.name, matcher)

  def string2Id(string: String, matcher: Regex = idMatcher) = string match {
    case matcher(_*) ⇒ string
    case _           ⇒ Hash.hexSha1(string)
  }
}