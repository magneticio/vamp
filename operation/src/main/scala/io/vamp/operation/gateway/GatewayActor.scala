package io.vamp.operation.gateway

import akka.pattern.ask
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common.config.Config
import io.vamp.model.artifact._
import io.vamp.model.reader.{ GatewayRouteValidation, Percentage }
import io.vamp.operation.notification._
import io.vamp.persistence.db.{ ArtifactPaginationSupport, PersistenceActor }
import io.vamp.persistence.operation.InnerGateway

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

object GatewayActor {

  implicit val timeout = PersistenceActor.timeout

  private val config = Config.config("vamp.operation.gateway")

  val virtualHostsEnabled: Boolean = config.boolean("virtual-hosts.enabled")

  val virtualHostsFormat1: String = config.string("virtual-hosts.formats.gateway")
  val virtualHostsFormat2: String = config.string("virtual-hosts.formats.deployment-port")
  val virtualHostsFormat3: String = config.string("virtual-hosts.formats.deployment-cluster-port")

  trait GatewayMessage

  case class Create(gateway: Gateway, source: Option[String], validateOnly: Boolean, force: Boolean = false) extends GatewayMessage

  case class Update(gateway: Gateway, source: Option[String], validateOnly: Boolean, promote: Boolean, force: Boolean = false) extends GatewayMessage

  case class Delete(name: String, validateOnly: Boolean, force: Boolean = false) extends GatewayMessage

  case class PromoteInner(gateway: Gateway) extends GatewayMessage

}

class GatewayActor extends ArtifactPaginationSupport with CommonSupportForActors with OperationNotificationProvider with GatewayRouteValidation {

  import GatewayActor._

  def receive = {

    case Create(gateway, source, validateOnly, force) ⇒ reply {
      create(gateway, source, validateOnly, force)
    }

    case Update(gateway, source, validateOnly, promote, force) ⇒ reply {
      update(gateway, source, validateOnly, promote, force)
    }

    case Delete(name, validateOnly, force) ⇒ reply {
      delete(name, validateOnly, force)
    }

    case PromoteInner(gateway) ⇒ reply {
      promote(gateway)
    }

    case any ⇒ unsupported(UnsupportedGatewayRequest(any))
  }

  private def create(gateway: Gateway, source: Option[String], validateOnly: Boolean, force: Boolean): Future[Any] = (gateway.inner, force, validateOnly) match {
    case (true, false, _) ⇒ Future.failed(reportException(InnerGatewayCreateError(gateway.name)))
    case (_, _, true)     ⇒ Try((process andThen validate andThen validateUniquePort)(gateway) :: Nil).recover({ case e ⇒ Future.failed(e) }).map(Future.successful).get
    case _                ⇒ Try((process andThen validate andThen validateUniquePort andThen persistFuture(source, create = true, promote = false))(gateway)).recover({ case e ⇒ Future.failed(e) }).get
  }

  private def update(gateway: Gateway, source: Option[String], validateOnly: Boolean, promote: Boolean, force: Boolean): Future[Any] = {

    def default = {
      if (validateOnly)
        Try((process andThen validate)(gateway) :: Nil).recover({ case e ⇒ Future.failed(e) }).map(Future.successful).get
      else
        Try((process andThen validate andThen persist(source, create = false, promote))(gateway)).recover({ case e ⇒ Future.failed(e) }).get
    }

    if (gateway.inner && !force) routeChanged(gateway) flatMap {
      case true  ⇒ Future.failed(reportException(InnerGatewayUpdateError(gateway.name)))
      case false ⇒ default
    }
    else default
  }

  private def delete(name: String, validateOnly: Boolean, force: Boolean): Future[Any] = {

    def default = if (validateOnly) Future.successful(None) else actorFor[PersistenceActor] ? PersistenceActor.Delete(name, classOf[Gateway])

    if (Gateway.inner(name) && !force) deploymentExists(name) flatMap {
      case true  ⇒ Future.failed(reportException(InnerGatewayRemoveError(name)))
      case false ⇒ default
    }
    else default
  }

  private def promote(gateway: Gateway) = {
    persist(None, create = false, promote = true)(gateway)
  }

  private def routeChanged(gateway: Gateway): Future[Boolean] = {
    checked[Option[_]](actorFor[PersistenceActor] ? PersistenceActor.Read(gateway.name, classOf[Gateway])) map {
      case Some(old: Gateway) ⇒ old.routes.map(_.path.normalized).toSet != gateway.routes.map(_.path.normalized).toSet
      case _                  ⇒ true
    }
  }

  private def deploymentExists(name: String): Future[Boolean] = {
    checked[Option[_]](actorFor[PersistenceActor] ? PersistenceActor.Read(GatewayPath(name).segments.head, classOf[Deployment])) map {
      case result ⇒ result.isDefined
    }
  }

  private def process: Gateway ⇒ Gateway = { gateway ⇒

    val updatedWeights = if (gateway.routes.forall(_.isInstanceOf[DefaultRoute])) {

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

        } else gateway

      } else gateway

    } else gateway

    val routes = updatedWeights.routes.map(_.asInstanceOf[DefaultRoute]).map { route ⇒
      val default = if (route.hasConditions) 100 else 0
      route.copy(conditionStrength = Option(route.conditionStrength.getOrElse(Percentage(default))))
    }

    updatedWeights.copy(routes = routes)
  }

  private def validate: Gateway ⇒ Gateway = validateGatewayRouteWeights andThen validateGatewayRouteConditionStrengths

  private def validateUniquePort: Gateway ⇒ Future[Gateway] = {
    case gateway if gateway.inner ⇒ Future.successful(gateway)
    case gateway ⇒
      allArtifacts[Gateway] map {
        case gateways if gateway.port.number != 0 ⇒
          gateways.find(_.port.number == gateway.port.number).map(g ⇒ throwException(UnavailableGatewayPortError(gateway.port, g)))
          gateway

        case _ ⇒ gateway
      }
  }

  private def persistFuture(source: Option[String], create: Boolean, promote: Boolean): Future[Gateway] ⇒ Future[Any] = { future ⇒
    future flatMap {
      case gateway ⇒ persist(source, create, promote)(gateway)
    }
  }

  private def persist(source: Option[String], create: Boolean, promote: Boolean): Gateway ⇒ Future[Any] = { gateway ⇒

    val virtualHosts = if (virtualHostsEnabled) defaultVirtualHosts(gateway) ++ gateway.virtualHosts else gateway.virtualHosts

    val g = gateway.copy(virtualHosts = virtualHosts.distinct)

    val requests = {
      val artifacts = (g.inner, promote) match {
        case (true, true)  ⇒ InnerGateway(g) :: g :: Nil
        case (true, false) ⇒ InnerGateway(g) :: Nil
        case _             ⇒ g :: Nil
      }

      artifacts.map {
        case artifact ⇒ if (create) PersistenceActor.Create(artifact, source) else PersistenceActor.Update(artifact, source)
      }
    }

    Future.sequence(requests.map {
      request ⇒ actorFor[PersistenceActor] ? request
    }).map(_ ⇒ g)
  }

  private def defaultVirtualHosts(gateway: Gateway): List[String] = GatewayPath(gateway.name).segments.map { domain ⇒
    if (domain.matches("^[\\d\\p{L}].*$")) domain.replaceAll("[^\\p{L}\\d]", "-") else domain
  } match {
    case g :: Nil           ⇒ virtualHostsFormat1.replaceAllLiterally(s"$$gateway", g) :: Nil
    case d :: p :: Nil      ⇒ virtualHostsFormat2.replaceAllLiterally(s"$$deployment", d).replaceAllLiterally(s"$$port", p) :: Nil
    case d :: c :: p :: Nil ⇒ virtualHostsFormat3.replaceAllLiterally(s"$$deployment", d).replaceAllLiterally(s"$$cluster", c).replaceAllLiterally(s"$$port", p) :: Nil
    case _                  ⇒ Nil
  }
}
