package io.vamp.gateway_driver.zookeeper

import io.vamp.common.akka.Bootstrap.{ Shutdown, Start }
import io.vamp.common.akka._
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.gateway_driver.model.Gateway
import io.vamp.gateway_driver.notification._
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.Future

class ZooKeeperGatewayStoreActor extends PulseFailureNotifier with CommonSupportForActors with GatewayDriverNotificationProvider {

  import io.vamp.gateway_driver.GatewayStore._

  def receive = {
    case Start                ⇒ start()
    case Shutdown             ⇒ shutdown()
    case InfoRequest          ⇒ reply(info)
    case Read                 ⇒ reply(read)
    case Write(gateways, raw) ⇒ write(gateways, raw)
    case other                ⇒ unsupported(UnsupportedGatewayStoreRequest(other))
  }

  override def errorNotificationClass = classOf[GatewayStoreResponseError]

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)

  private def start() = {}

  private def shutdown() = {}

  private def info = Future.successful("")

  private def read: Future[List[Gateway]] = Future.successful(Nil)

  private def write(gateways: List[Gateway], raw: Option[Array[Byte]]) = {}
}