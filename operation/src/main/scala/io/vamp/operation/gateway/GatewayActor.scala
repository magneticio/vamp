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

  case class Create(gateway: Gateway, source: Option[String], validateOnly: Boolean) extends GatewayMessage

  case class Update(gateway: Gateway, source: Option[String], validateOnly: Boolean) extends GatewayMessage

  case class Delete(name: String, validateOnly: Boolean, force: Boolean = false) extends GatewayMessage

}

class GatewayActor extends CommonSupportForActors with OperationNotificationProvider {

  import GatewayActor._

  private implicit val timeout = PersistenceActor.timeout

  def receive = {

    case Create(gateway, source, validateOnly) ⇒ reply(create(gateway, source, validateOnly))

    case Update(gateway, source, validateOnly) ⇒ reply(update(gateway, source, validateOnly))

    case Delete(name, validateOnly, force)     ⇒ reply(delete(name, validateOnly, force))

    case any                                   ⇒ unsupported(UnsupportedGatewayRequest(any))
  }

  private def create(gateway: Gateway, source: Option[String], validateOnly: Boolean): Future[Any] = {
    if (validateOnly) Future(gateway) else actorFor[PersistenceActor] ? PersistenceActor.Create(gateway, source)
  }

  private def update(gateway: Gateway, source: Option[String], validateOnly: Boolean): Future[Any] = {
    if (validateOnly) Future(gateway) else actorFor[PersistenceActor] ? PersistenceActor.Update(gateway, source)
  }

  private def delete(name: String, validateOnly: Boolean, force: Boolean): Future[Any] = {
    if (GatewayPath(name).segments.size == 3 && !force)
      Future.failed(reportException(UnsupportedGatewayRemovalError(name)))
    else if (validateOnly)
      Future(None)
    else
      actorFor[PersistenceActor] ? PersistenceActor.Delete(name, classOf[Gateway])
  }
}
