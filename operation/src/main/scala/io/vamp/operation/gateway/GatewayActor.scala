package io.vamp.operation.gateway

import akka.pattern.ask
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.model.artifact._
import io.vamp.operation.notification._
import io.vamp.persistence.db.PersistenceActor

import scala.concurrent.Future

object GatewayActor {

  trait GatewayMessage

  case class Create(gateway: Gateway, source: Option[String], validateOnly: Boolean, force: Boolean = false) extends GatewayMessage

  case class Update(gateway: Gateway, source: Option[String], validateOnly: Boolean, force: Boolean = false) extends GatewayMessage

  case class Delete(name: String, validateOnly: Boolean, force: Boolean = false) extends GatewayMessage

}

class GatewayActor extends CommonSupportForActors with OperationNotificationProvider {

  import GatewayActor._

  private implicit val timeout = PersistenceActor.timeout

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
    case (_, _, true)     ⇒ Future.successful(None)
    case _                ⇒ actorFor[PersistenceActor] ? PersistenceActor.Create(gateway, source)
  }

  private def update(gateway: Gateway, source: Option[String], validateOnly: Boolean, force: Boolean): Future[Any] = {

    def default = if (validateOnly) Future.successful(gateway) else actorFor[PersistenceActor] ? PersistenceActor.Update(gateway, source)

    if (internal(gateway.name) && !force)
      routeChanged(gateway) flatMap {
        case true  ⇒ Future.failed(reportException(InnerGatewayUpdateError(gateway.name)))
        case false ⇒ default
      }
    else default
  }

  private def delete(name: String, validateOnly: Boolean, force: Boolean): Future[Any] = (internal(name), force, validateOnly) match {
    case (true, false, _) ⇒ Future.failed(reportException(InnerGatewayRemoveError(name)))
    case (_, _, true)     ⇒ Future.successful(None)
    case _                ⇒ actorFor[PersistenceActor] ? PersistenceActor.Delete(name, classOf[Gateway])
  }

  private def internal(name: String) = GatewayPath(name).segments.size == 3

  private def routeChanged(gateway: Gateway): Future[Boolean] = {
    checked[Option[_]](actorFor[PersistenceActor] ? PersistenceActor.Read(gateway.name, classOf[Gateway])) map {
      case Some(old: Gateway) ⇒ old.routes.map(_.path.normalized).toSet != gateway.routes.map(_.path.normalized).toSet
      case _                  ⇒ true
    }
  }
}
