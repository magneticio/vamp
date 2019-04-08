package io.vamp.operation.gateway

import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common.{ Config, ConfigMagnet }
import io.vamp.model.artifact._
import io.vamp.model.reader.{ GatewayRouteValidation, Percentage }
import io.vamp.operation.notification._
import io.vamp.persistence.{ ArtifactPaginationSupport, PersistenceActor }

import scala.concurrent.Future
import scala.util.Try

object GatewayActor {

  val timeout: ConfigMagnet[Timeout] = PersistenceActor.timeout

  val virtualHostsEnabled: ConfigMagnet[Boolean] = Config.boolean("vamp.operation.gateway.virtual-hosts.enabled")
  val virtualHostsFormat1: ConfigMagnet[String] = Config.string("vamp.operation.gateway.virtual-hosts.formats.gateway")
  val virtualHostsFormat2: ConfigMagnet[String] = Config.string("vamp.operation.gateway.virtual-hosts.formats.deployment-port")
  val virtualHostsFormat3: ConfigMagnet[String] = Config.string("vamp.operation.gateway.virtual-hosts.formats.deployment-cluster-port")

  trait GatewayMessage

  case class Create(gateway: Gateway, source: Option[String], validateOnly: Boolean, force: Boolean = false) extends GatewayMessage

  case class Update(gateway: Gateway, source: Option[String], validateOnly: Boolean, force: Boolean = false) extends GatewayMessage

  case class Delete(name: String, validateOnly: Boolean, force: Boolean = false) extends GatewayMessage

}

class GatewayActor extends ArtifactPaginationSupport with CommonSupportForActors with OperationNotificationProvider with GatewayRouteValidation with LazyLogging with RouteComparator {

  import GatewayActor._

  private implicit val timeout: Timeout = PersistenceActor.timeout()

  def receive: Actor.Receive = {

    case Create(gateway, source, validateOnly, force) ⇒ reply {
      create(gateway, source, validateOnly, force)
    }

    case Update(gateway, source, validateOnly, force) ⇒ reply {
      update(gateway, source, validateOnly, force)
    }

    case Delete(name, validateOnly, force) ⇒ reply {
      delete(name, validateOnly, force)
    }

    case any ⇒ unsupported(UnsupportedGatewayRequest(any))
  }

  private def create(gateway: Gateway, source: Option[String], validateOnly: Boolean, force: Boolean): Future[Any] = (gateway.internal, force, validateOnly) match {
    case (true, false, _) ⇒ Future.failed(reportException(InternalGatewayCreateError(gateway.name)))
    case (_, _, true)     ⇒ Try((process andThen validate andThen validateUniquePort)(gateway)).recover({ case e ⇒ Future.failed(e) }).get
    case _                ⇒ Try((process andThen validate andThen validateUniquePort andThen persistFuture(source, create = true))(gateway)).recover({ case e ⇒ Future.failed(e) }).get
  }

  private def update(gateway: Gateway, source: Option[String], validateOnly: Boolean, force: Boolean): Future[Any] = {

    def default = {
      if (validateOnly)
        Try((process andThen validate)(gateway)).recover({ case e ⇒ Future.failed(e) }).map(Future.successful).get
      else
        Try((process andThen validate andThen persist(source, create = false))(gateway)).recover({ case e ⇒ Future.failed(e) }).get
    }

    if (!validateOnly) {
      // Route changes should be checked for both external internal gateways
      checkRouteChanges(gateway)
    }

    if (gateway.internal && !force) routeChanged(gateway) flatMap {
      case true  ⇒ Future.failed(reportException(InternalGatewayUpdateError(gateway.name)))
      case false ⇒ default
    }
    else default
  }

  private def delete(name: String, validateOnly: Boolean, force: Boolean): Future[Any] = {

    def default = {
      if (validateOnly) Future.successful(None)
      else actorFor[PersistenceActor] ? PersistenceActor.Delete(name, classOf[Gateway])
    }

    if (Gateway.internal(name) && !force) deploymentExists(name) flatMap {
      case true  ⇒ Future.failed(reportException(InternalGatewayRemoveError(name)))
      case false ⇒ default
    }
    else default
  }

  private def checkRouteChanges(gateway: Gateway): Unit = {
    checked[Option[_]](actorFor[PersistenceActor] ? PersistenceActor.Read(gateway.name, classOf[Gateway])) map {
      case Some(old: Gateway) ⇒
        compareNewRoutesAndGenerateEvents(old, gateway, "routeChanged")
      case _ ⇒ logger.debug(s"Gateway ${gateway.name} doesn't exist")
    }
  }

  private def routeChanged(gateway: Gateway): Future[Boolean] = {
    checked[Option[_]](actorFor[PersistenceActor] ? PersistenceActor.Read(gateway.name, classOf[Gateway])) map {
      case Some(old: Gateway) ⇒
        old.routes.map(_.path.normalized).toSet != gateway.routes.map(_.path.normalized).toSet
      case _ ⇒ true
    }
  }

  private def deploymentExists(name: String): Future[Boolean] = {
    checked[Option[_]](actorFor[PersistenceActor] ? PersistenceActor.Read(GatewayPath(name).segments.head, classOf[Deployment])) map {
      result ⇒ result.isDefined
    }
  }

  private def process: Gateway ⇒ Gateway = { gateway ⇒

    val newGateway = if (gateway.routes.forall(_.isInstanceOf[DefaultRoute])) {

      val allRoutes = gateway.routes.map(_.asInstanceOf[DefaultRoute])

      val availableWeight = 100 - allRoutes.flatMap(_.weight.map(_.value)).sum

      if (availableWeight >= 0) {

        val (noWeightRoutes, weightRoutes) = allRoutes.partition(_.weight.isEmpty)

        if (noWeightRoutes.nonEmpty) {
          val weight = Math.round(availableWeight / noWeightRoutes.size)

          val routes = noWeightRoutes.view.zipWithIndex.toList.map {
            case (route, index) ⇒
              val calculated = if (index == noWeightRoutes.size - 1) availableWeight - index * weight else weight
              route.copy(weight = Option(Percentage(calculated)))
          }

          gateway.copy(routes = routes ++ weightRoutes)

        }
        else gateway

      }
      else gateway

    }
    else gateway

    val routes = newGateway.routes.map(_.asInstanceOf[DefaultRoute]).map { route ⇒
      val default = if (route.definedCondition) 100 else 0
      route.copy(conditionStrength = Option(route.conditionStrength.getOrElse(Percentage(default))))
    }
    val gatewayWithUpdatedRoutes = newGateway.copy(routes = routes)
    compareNewRoutesAndGenerateEvents(gateway, gatewayWithUpdatedRoutes, "process")
    gatewayWithUpdatedRoutes
  }

  private def validate: Gateway ⇒ Gateway = validateGatewayRouteWeights andThen validateGatewayRouteConditionStrengths

  private def validateUniquePort: Gateway ⇒ Future[Gateway] = {
    case gateway if gateway.internal ⇒ Future.successful(gateway)
    case gateway ⇒
      consume(allArtifacts[Gateway]) map {
        case gateways if gateway.port.number != 0 ⇒
          gateways.find(_.port.number == gateway.port.number).map(g ⇒ throwException(UnavailableGatewayPortError(gateway.port, g)))
          gateway
        case _ ⇒ gateway
      }
  }

  private def persistFuture(source: Option[String], create: Boolean): Future[Gateway] ⇒ Future[Any] = { future ⇒
    future flatMap {
      gateway ⇒ persist(source, create)(gateway)
    }
  }

  private def persist(source: Option[String], create: Boolean): Gateway ⇒ Future[Any] = {
    case gateway if gateway.name.nonEmpty ⇒
      val virtualHosts = if (virtualHostsEnabled()) defaultVirtualHosts(gateway) ++ gateway.virtualHosts else gateway.virtualHosts
      val g = gateway.copy(virtualHosts = virtualHosts.distinct)

      val request = if (create) PersistenceActor.Create(g, source) else PersistenceActor.Update(g, source)

      (actorFor[PersistenceActor] ? request).map(_ ⇒ g)

    case _ ⇒ Future.successful(None)
  }

  private def defaultVirtualHosts(gateway: Gateway): List[String] = GatewayPath(gateway.name).segments.map { domain ⇒
    if (domain.matches("^[\\d\\p{L}].*$")) domain.replaceAll("[^\\p{L}\\d]", "-") else domain
  } match {
    case g :: Nil           ⇒ virtualHostsFormat1().replaceAllLiterally(s"$$gateway", g) :: Nil
    case d :: p :: Nil      ⇒ virtualHostsFormat2().replaceAllLiterally(s"$$deployment", d).replaceAllLiterally(s"$$port", p) :: Nil
    case d :: c :: p :: Nil ⇒ virtualHostsFormat3().replaceAllLiterally(s"$$deployment", d).replaceAllLiterally(s"$$cluster", c).replaceAllLiterally(s"$$port", p) :: Nil
    case _                  ⇒ Nil
  }

}
