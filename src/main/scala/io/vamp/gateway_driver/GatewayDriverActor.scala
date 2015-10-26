package io.vamp.gateway_driver

import io.vamp.common.akka._
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.model.artifact._
import io.vamp.pulse.notification.PulseFailureNotifier
import io.vamp.gateway_driver.notification.{ GatewayDriverNotificationProvider, GatewayResponseError, UnsupportedGatewayDriverRequest }

import scala.language.implicitConversions

object GatewayDriverActor {

  trait GatewayDriverMessage

  object All extends GatewayDriverMessage

  case class Create(deployment: Deployment, cluster: DeploymentCluster, port: Port, update: Boolean) extends GatewayDriverMessage

  case class CreateEndpoint(deployment: Deployment, port: Port, update: Boolean) extends GatewayDriverMessage

  case class Remove(deployment: Deployment, cluster: DeploymentCluster, port: Port) extends GatewayDriverMessage

  case class RemoveEndpoint(deployment: Deployment, port: Port) extends GatewayDriverMessage

}

class GatewayDriverActor(driver: RouterDriver) extends PulseFailureNotifier with CommonSupportForActors with GatewayDriverNotificationProvider {

  import io.vamp.gateway_driver.GatewayDriverActor._

  def receive = {
    case InfoRequest                               ⇒ reply(driver.info)
    case All                                       ⇒ reply(driver.all)
    case Create(deployment, cluster, port, update) ⇒ reply(driver.create(deployment, cluster, port, update))
    case Remove(deployment, cluster, port)         ⇒ reply(driver.remove(deployment, cluster, port))
    case CreateEndpoint(deployment, port, update)  ⇒ reply(driver.create(deployment, port, update))
    case RemoveEndpoint(deployment, port)          ⇒ reply(driver.remove(deployment, port))
    case other                                     ⇒ unsupported(UnsupportedGatewayDriverRequest(other))
  }

  override def errorNotificationClass = classOf[GatewayResponseError]

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)
}
