package io.vamp.gateway_driver

import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.gateway_driver.kibana.KibanaDashboardActor
import io.vamp.gateway_driver.notification.{ GatewayDriverNotificationProvider, GatewayDriverResponseError, UnsupportedGatewayDriverRequest }
import io.vamp.model.artifact._
import io.vamp.persistence.kv.KeyValueStoreActor
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.language.postfixOps

object GatewayDriverActor {

  val path = "gateways" :: Nil

  trait GatewayDriverMessage

  case class Commit(gateways: List[Gateway]) extends GatewayDriverMessage

}

class GatewayDriverActor(marshaller: GatewayMarshaller) extends PulseFailureNotifier with CommonSupportForActors with GatewayDriverNotificationProvider {

  import GatewayDriverActor._

  lazy implicit val timeout = KeyValueStoreActor.timeout

  private def path = GatewayDriverActor.path ++ marshaller.path

  def receive = {
    case InfoRequest      ⇒ reply(info)
    case Commit(gateways) ⇒ commit(gateways)
    case other            ⇒ unsupported(UnsupportedGatewayDriverRequest(other))
  }

  override def errorNotificationClass = classOf[GatewayDriverResponseError]

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)

  private def info = IoC.actorFor[KibanaDashboardActor] ? InfoRequest map {
    case kibana ⇒ Map("marshaller" -> marshaller.info, "kibana" -> kibana)
  }

  private def commit(gateways: List[Gateway]) = {
    def send(value: String) = IoC.actorFor[KeyValueStoreActor] ! KeyValueStoreActor.Set(path, Option(value))

    val content = marshaller.marshall(gateways)
    implicit val timeout = KeyValueStoreActor.timeout

    IoC.actorFor[KeyValueStoreActor] ? KeyValueStoreActor.Get(path) map {
      case Some(value: String) ⇒ if (value != content) send(content)
      case _                   ⇒ send(content)
    }
  }
}
