package io.vamp.operation.gateway

import akka.pattern.ask
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.model.artifact._
import io.vamp.model.notification.{ DuplicateGatewayPortError, GatewayRouteWeightError }
import io.vamp.model.reader.Percentage
import io.vamp.operation.notification._
import io.vamp.persistence.db.{ ArtifactPaginationSupport, PersistenceActor }
import io.vamp.persistence.operation.InnerGateway

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

object GatewayActor {

  implicit val timeout = PersistenceActor.timeout

  trait GatewayMessage

  case class Create(gateway: Gateway, source: Option[String], validateOnly: Boolean, force: Boolean = false) extends GatewayMessage

  case class Update(gateway: Gateway, source: Option[String], validateOnly: Boolean, promote: Boolean, force: Boolean = false) extends GatewayMessage

  case class Delete(name: String, validateOnly: Boolean, force: Boolean = false) extends GatewayMessage

  case class PromoteInner(gateway: Gateway) extends GatewayMessage

}

class GatewayActor extends ArtifactPaginationSupport with CommonSupportForActors with OperationNotificationProvider {

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
    case (_, _, true)     ⇒ Try((process andThen validate)(gateway) :: Nil).recover({ case e ⇒ Future.failed(e) }).map(Future.successful).get
    case _                ⇒ Try((process andThen validate andThen purge andThen persist(source, create = true))(gateway)).recover({ case e ⇒ Future.failed(e) }).get
  }

  private def update(gateway: Gateway, source: Option[String], validateOnly: Boolean, promote: Boolean, force: Boolean): Future[Any] = {

    def default = {
      if (validateOnly)
        Try((process andThen validate)(gateway) :: Nil).recover({ case e ⇒ Future.failed(e) }).map(Future.successful).get
      else
        Try((process andThen validate andThen purge andThen persist(source, create = false, promote))(gateway)).recover({ case e ⇒ Future.failed(e) }).get
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
    persist(None, create = false, promote = true)(Future.successful(gateway))
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

    if (gateway.routes.forall(_.isInstanceOf[DefaultRoute])) {

      val (withFilters, withoutFilters) = gateway.routes.map(_.asInstanceOf[DefaultRoute]).partition(_.hasRoutingFilters)

      val withFiltersUpdated = withFilters.map { route ⇒ route.copy(weight = Option(route.weight.getOrElse(Percentage(100)))) }

      val availableWeight = 100 - withoutFilters.flatMap(_.weight.map(_.value)).sum

      if (availableWeight >= 0) {

        val (noWeightRoutes, weightRoutes) = withoutFilters.partition(_.weight.isEmpty)

        if (noWeightRoutes.nonEmpty) {
          val weight = Math.round(availableWeight / noWeightRoutes.size)

          val routes = noWeightRoutes.view.zipWithIndex.toList.map {
            case (route, index) ⇒
              val calculated = if (index == noWeightRoutes.size - 1) availableWeight - index * weight else weight
              route.copy(weight = Option(Percentage(calculated)))
          }

          gateway.copy(routes = withFiltersUpdated ++ routes ++ weightRoutes)

        } else gateway.copy(routes = withFiltersUpdated ++ withoutFilters)

      } else gateway.copy(routes = withFiltersUpdated ++ withoutFilters)

    } else gateway
  }

  private def validate: Gateway ⇒ Gateway = { gateway ⇒

    val (withFilters, withoutFilters) = gateway.routes.filter(_.isInstanceOf[DefaultRoute]).map(_.asInstanceOf[DefaultRoute]).partition(_.hasRoutingFilters)

    val weights = withoutFilters.flatMap(_.weight)
    if (weights.exists(_.value < 0) || weights.map(_.value).sum > 100) throwException(GatewayRouteWeightError(gateway))

    if (withFilters.flatMap(_.weight).exists(weight ⇒ weight.value < 0 || weight.value > 100)) throwException(GatewayRouteWeightError(gateway))

    gateway
  }

  private def purge: Gateway ⇒ Future[Gateway] = {
    case gateway if gateway.inner ⇒ Future.successful(gateway)
    case gateway ⇒
      allArtifacts[Gateway] flatMap {
        case gateways ⇒
          val processed = gateways.map {
            case g if g.name != gateway.name && g.port.number == gateway.port.number ⇒
              if (g.hasRouteTargets) throwException(DuplicateGatewayPortError(g.port.number))
              actorFor[PersistenceActor] ? PersistenceActor.Delete(g.name, classOf[Gateway])
            case g ⇒ Future.successful(g)
          }
          Future.sequence(processed)
      } map {
        case _ ⇒ gateway
      }
  }

  private def persist(source: Option[String], create: Boolean, promote: Boolean = false): Future[Gateway] ⇒ Future[Any] = { future ⇒
    future.flatMap { gateway ⇒

      val requests = {
        val artifacts = (gateway.inner, promote) match {
          case (true, true)  ⇒ InnerGateway(gateway) :: gateway :: Nil
          case (true, false) ⇒ InnerGateway(gateway) :: Nil
          case _             ⇒ gateway :: Nil
        }

        artifacts.map {
          case artifact ⇒ if (create) PersistenceActor.Create(artifact, source) else PersistenceActor.Update(artifact, source)
        }
      }

      Future.sequence(requests.map {
        request ⇒ actorFor[PersistenceActor] ? request
      }).map(_ ⇒ gateway)
    }
  }
}
