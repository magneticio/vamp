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

  val rootPath = "gateways" :: Nil

  def templatePath(kind: String, name: String) = rootPath :+ kind :+ name :+ "template"

  def configurationPath(kind: String, name: String) = rootPath :+ kind :+ name :+ "configuration"

  sealed trait GatewayDriverMessage

  object Pull extends GatewayDriverMessage

  case class Push(gateways: List[Gateway]) extends GatewayDriverMessage
}

class GatewayDriverActor(marshallers: Map[String, GatewayMarshaller]) extends PersistenceMarshaller with PulseFailureNotifier with CommonSupportForActors with GatewayDriverNotificationProvider {

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

  private def info = Future.successful {
    Map("marshallers" → marshallers.map {
      case (name, marshaller) ⇒ name → marshaller.info
    })
  }

  private def pull(): Future[List[Gateway]] = {
    IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get(rootPath) map {
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

    send(rootPath, marshall(gateways))

    marshallers.foreach {
      case (name, marshaller) ⇒
        val content = if (marshaller.keyValue) {
          IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get(templatePath(marshaller.`type`, name)) map {
            case Some(content: String) ⇒ Option(content)
            case _                     ⇒ None
          }
        }
        else Future.successful(None)
        content.map { content ⇒ send(configurationPath(marshaller.`type`, name), marshaller.marshall(gateways, content)) }
    }
  }
}
