package io.vamp.gateway_driver

import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.gateway_driver.notification.{ GatewayDriverNotificationProvider, GatewayDriverResponseError, UnsupportedGatewayDriverRequest }
import io.vamp.model.artifact._
import io.vamp.persistence.db.PersistenceMarshaller
import io.vamp.persistence.kv.KeyValueStoreActor
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.Future

object GatewayDriverActor {

  val root = "gateways"

  sealed trait GatewayDriverMessage

  object Pull extends GatewayDriverMessage

  case class Push(gateways: List[Gateway]) extends GatewayDriverMessage
}

class GatewayDriverActor(marshaller: GatewayMarshaller) extends PersistenceMarshaller with PulseFailureNotifier with CommonSupportForActors with GatewayDriverNotificationProvider {

  import GatewayDriverActor._

  lazy implicit val timeout = KeyValueStoreActor.timeout()

  def receive = {
    case InfoRequest    ⇒ reply(info)
    case Pull           ⇒ reply(pull())
    case Push(gateways) ⇒ push(gateways)
    case other          ⇒ unsupported(UnsupportedGatewayDriverRequest(other))
  }

  override def errorNotificationClass = classOf[GatewayDriverResponseError]

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)

  private def info = Future.successful(Map("marshaller" → marshaller.info))

  private def pull(): Future[List[Gateway]] = {
    IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get(root :: Nil) map {
      case Some(content: String) ⇒ unmarshall[Gateway](content)
      case _                     ⇒ Nil
    }
  }

  private def push(gateways: List[Gateway]) = {

    def send(path: List[String], value: String) = {
      IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get(path) map {
        case Some(content: String) if value == content ⇒
        case _                                         ⇒ IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Set(path, Option(value))
      }
    }

    send(root :: Nil, marshall(gateways))
    send(root +: marshaller.path, marshaller.marshall(gateways))
  }
}
