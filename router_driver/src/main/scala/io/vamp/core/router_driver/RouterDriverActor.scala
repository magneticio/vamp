package io.vamp.core.router_driver

import akka.actor.Props
import io.vamp.common.akka._
import io.vamp.common.vitals.InfoRequest
import io.vamp.core.model.artifact._
import io.vamp.core.router_driver.notification.{RouterDriverNotificationProvider, RouterResponseError, UnsupportedRouterDriverRequest}

object RouterDriverActor extends ActorDescription {

  def props(args: Any*): Props = Props(classOf[RouterDriverActor], args: _*)

  trait RouterDriverMessage

  object All extends RouterDriverMessage

  case class Create(deployment: Deployment, cluster: DeploymentCluster, port: Port, update: Boolean) extends RouterDriverMessage

  case class CreateEndpoint(deployment: Deployment, port: Port, update: Boolean) extends RouterDriverMessage

  case class Remove(deployment: Deployment, cluster: DeploymentCluster, port: Port) extends RouterDriverMessage

  case class RemoveEndpoint(deployment: Deployment, port: Port) extends RouterDriverMessage

}

class RouterDriverActor(driver: RouterDriver) extends CommonSupportForActors with ReplyActor with RouterDriverNotificationProvider {

  import io.vamp.core.router_driver.RouterDriverActor._

  def receive = {
    case InfoRequest => reply(driver.info)
    case All => reply(driver.all)
    case Create(deployment, cluster, port, update) => reply(driver.create(deployment, cluster, port, update))
    case Remove(deployment, cluster, port) => reply(driver.remove(deployment, cluster, port))
    case CreateEndpoint(deployment, port, update) => reply(driver.create(deployment, port, update))
    case RemoveEndpoint(deployment, port) => reply(driver.remove(deployment, port))
    case other => unsupported(UnsupportedRouterDriverRequest(other))
  }

  override def errorNotificationClass = classOf[RouterResponseError]
}
