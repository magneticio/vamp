package io.vamp.gateway_driver

import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.gateway_driver.notification.{ GatewayDriverNotificationProvider, GatewayDriverResponseError, UnsupportedGatewayDriverRequest }
import io.vamp.model.artifact._
import io.vamp.model.event.Event
import io.vamp.persistence.{ KeyValueStoreActor, PersistenceMarshaller }
import io.vamp.pulse.PulseActor
import io.vamp.pulse.PulseActor.Publish
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.Future

object GatewayDriverActor {

  val rootPath: List[String] = Gateway.kind :: Nil

  sealed trait GatewayDriverMessage

  object Pull extends GatewayDriverMessage

  case class Push(gateways: List[Gateway]) extends GatewayDriverMessage

  case class GetTemplate(`type`: String, name: String) extends GatewayDriverMessage

  case class SetTemplate(`type`: String, name: String, template: String) extends GatewayDriverMessage

  case class GetConfiguration(`type`: String, name: String) extends GatewayDriverMessage
}

case class GatewayMarshallerDefinition(marshaller: GatewayMarshaller, template: String)

class GatewayDriverActor(marshallers: Map[String, GatewayMarshallerDefinition]) extends PersistenceMarshaller with PulseFailureNotifier with CommonSupportForActors with GatewayDriverNotificationProvider {

  import GatewayDriverActor._

  lazy implicit val timeout = KeyValueStoreActor.timeout()

  def receive = {
    case InfoRequest         ⇒ reply(info)
    case r: GetTemplate      ⇒ reply(getTemplate(r.`type`, r.name))
    case r: SetTemplate      ⇒ reply(setTemplate(r.`type`, r.name, r.template))
    case r: GetConfiguration ⇒ reply(getConfiguration(r.`type`, r.name))
    case Pull                ⇒ reply(pull())
    case Push(gateways)      ⇒ push(gateways)
    case _: Event            ⇒
    case other               ⇒ unsupported(UnsupportedGatewayDriverRequest(other))
  }

  override def errorNotificationClass = classOf[GatewayDriverResponseError]

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)

  private def info = Future.successful {
    Map("marshallers" → marshallers.map {
      case (name, definition) ⇒ name → definition.marshaller.info
    })
  }

  private def get(path: List[String]): Future[Option[String]] = {
    IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get(path) map {
      case Some(content: String) ⇒ Option(content)
      case _                     ⇒ None
    }
  }

  private def set(path: List[String], value: String): Future[_] = {
    IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get(path) flatMap {
      case Some(content: String) if value == content ⇒ Future.successful(None)
      case _                                         ⇒ IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Set(path, Option(value))
    }
  }

  private def pull(): Future[List[Gateway]] = {
    IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get(rootPath) map {
      case Some(content: String) ⇒ unmarshall[Gateway](content)
      case _                     ⇒ Nil
    }
  }

  private def push(gateways: List[Gateway]) = {
    set(rootPath, marshall(gateways))
    marshallers.foreach {
      case (name, definition) ⇒ getTemplate(definition.marshaller.`type`, name).map { content ⇒
        set(configurationPath(definition.marshaller.`type`, name), definition.marshaller.marshall(gateways, content)).map {
          case None | false ⇒
          case stored ⇒
            IoC.actorFor[PulseActor] ! Publish(Event(tags(definition.marshaller.`type`, name, "configuration"), None), publishEventValue = false)
            stored
        }
      }
    }
  }

  private def getConfiguration(`type`: String, name: String): Future[String] = {
    get(configurationPath(`type`, name)).map(_.getOrElse(""))
  }

  private def getTemplate(`type`: String, name: String): Future[String] = {
    get(templatePath(`type`, name)).map {
      case Some(content) if content.trim.nonEmpty ⇒ content
      case _                                      ⇒ marshallers.get(name).map(_.template).getOrElse("")
    }
  }

  private def setTemplate(`type`: String, name: String, template: String) = {
    set(templatePath(`type`, name), template).map {
      case None | false ⇒
      case stored ⇒
        IoC.actorFor[PulseActor] ! Publish(Event(tags(`type`, name, "template"), None), publishEventValue = false)
        stored
    }
  }

  private def tags(`type`: String, name: String, action: String) = Set(s"marshaller:$action", s"type:${`type`}", s"name:$name")

  private def templatePath(kind: String, name: String) = rootPath :+ kind :+ name :+ "template"

  private def configurationPath(kind: String, name: String) = rootPath :+ kind :+ name :+ "configuration"
}
