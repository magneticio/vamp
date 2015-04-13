package io.vamp.core.router_driver

import _root_.io.vamp.common.akka._
import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.vitals.InfoRequest
import io.vamp.core.model.artifact._
import io.vamp.core.router_driver.notification.{RouterDriverNotificationProvider, RouterResponseError, UnsupportedRouterDriverRequest}

import scala.concurrent.duration._

object RouterDriverActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.core.router-driver.response-timeout").seconds)

  def props(args: Any*): Props = Props(classOf[RouterDriverActor], args: _*)

  trait RouterDriverMessage

  object All extends RouterDriverMessage

  case class Create(deployment: Deployment, cluster: DeploymentCluster, port: Port, update: Boolean) extends RouterDriverMessage

  case class CreateEndpoint(deployment: Deployment, port: Port, update: Boolean) extends RouterDriverMessage

  case class Remove(deployment: Deployment, cluster: DeploymentCluster, port: Port) extends RouterDriverMessage

  case class RemoveEndpoint(deployment: Deployment, port: Port) extends RouterDriverMessage

}

class RouterDriverActor(driver: RouterDriver) extends Actor with ActorLogging with ActorSupport with ReplyActor with FutureSupportNotification with ActorExecutionContextProvider with RouterDriverNotificationProvider {

  import io.vamp.core.router_driver.RouterDriverActor._

  implicit val timeout = RouterDriverActor.timeout

  override protected def requestType: Class[_] = classOf[RouterDriverMessage]

  override protected def errorRequest(request: Any): RequestError = UnsupportedRouterDriverRequest(request)

  def reply(request: Any) = try {
    request match {
      case InfoRequest => offload(driver.info, classOf[RouterResponseError])
      case All => offload(driver.all, classOf[RouterResponseError])
      case Create(deployment, cluster, port, update) => offload(driver.create(deployment, cluster, port, update), classOf[RouterResponseError])
      case Remove(deployment, cluster, port) => offload(driver.remove(deployment, cluster, port), classOf[RouterResponseError])
      case CreateEndpoint(deployment, port, update) => offload(driver.create(deployment, port, update), classOf[RouterResponseError])
      case RemoveEndpoint(deployment, port) => offload(driver.remove(deployment, port), classOf[RouterResponseError])
      case _ => unsupported(request)
    }
  } catch {
    case e: Exception => exception(RouterResponseError(e))
  }
}
