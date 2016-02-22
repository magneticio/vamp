package io.vamp.operation.gateway

import akka.pattern.ask
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.model.artifact._
import io.vamp.model.notification.GatewayRouteWeightError
import io.vamp.model.reader.Percentage
import io.vamp.operation.notification._
import io.vamp.persistence.db.PersistenceActor

import scala.concurrent.Future

object GatewayActor {

  implicit val timeout = PersistenceActor.timeout

  trait GatewayMessage

  case class Create(gateway: Gateway, source: Option[String], validateOnly: Boolean, force: Boolean = false) extends GatewayMessage

  case class Update(gateway: Gateway, source: Option[String], validateOnly: Boolean, force: Boolean = false) extends GatewayMessage

  case class Delete(name: String, validateOnly: Boolean, force: Boolean = false) extends GatewayMessage

}

class GatewayActor extends CommonSupportForActors with OperationNotificationProvider {

  import GatewayActor._

  def receive = {

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

  private def create(gateway: Gateway, source: Option[String], validateOnly: Boolean, force: Boolean): Future[Any] = (internal(gateway.name), force, validateOnly) match {
    case (true, false, _) ⇒ Future.failed(reportException(InnerGatewayCreateError(gateway.name)))
    case (_, _, true) ⇒
      try {
        Future.successful((process andThen validate)(gateway))
      } catch {
        case e: Exception ⇒ Future.failed(e)
      }
    case _ ⇒
      try {
        (process andThen validate andThen persist(source, create = true))(gateway)
      } catch {
        case e: Exception ⇒ Future.failed(e)
      }
  }

  private def update(gateway: Gateway, source: Option[String], validateOnly: Boolean, force: Boolean): Future[Any] = {

    def default = {
      if (validateOnly) Future.successful(gateway)
      else
        try {
          (process andThen validate andThen persist(source, create = false))(gateway)
        } catch {
          case e: Exception ⇒ Future.failed(e)
        }
    }

    if (internal(gateway.name) && !force) routeChanged(gateway) flatMap {
      case true  ⇒ Future.failed(reportException(InnerGatewayUpdateError(gateway.name)))
      case false ⇒ default
    }
    else default
  }

  private def delete(name: String, validateOnly: Boolean, force: Boolean): Future[Any] = {

    def default = if (validateOnly) Future.successful(None) else actorFor[PersistenceActor] ? PersistenceActor.Delete(name, classOf[Gateway])

    if (internal(name) && !force) deploymentExists(name) flatMap {
      case true  ⇒ Future.failed(reportException(InnerGatewayRemoveError(name)))
      case false ⇒ default
    }
    else default
  }

  private def internal(name: String) = GatewayPath(name).segments.size == 3

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

  private def persist(source: Option[String], create: Boolean): Gateway ⇒ Future[Any] = { gateway ⇒
    if (create)
      actorFor[PersistenceActor] ? PersistenceActor.Create(gateway, source)
    else
      actorFor[PersistenceActor] ? PersistenceActor.Update(gateway, source)
  }
}
