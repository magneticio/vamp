package io.magnetic.vamp_core.router_driver

import _root_.io.magnetic.vamp_common.akka._
import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_core.model.artifact._
import io.magnetic.vamp_core.router_driver.RouterDriverActor.{All, Remove, RouterDriveMessage, Update}
import io.magnetic.vamp_core.router_driver.notification.{RouterDriverNotificationProvider, RouterResponseError, UnsupportedRouterDriverRequest}

import scala.concurrent.duration._

object RouterDriverActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("deployment.router.response.timeout").seconds)

  def props(args: Any*): Props = Props(classOf[RouterDriverActor], args: _*)

  trait RouterDriveMessage

  object All extends RouterDriveMessage

  case class Update(deployment: Deployment, cluster: DeploymentCluster, port: Port) extends RouterDriveMessage

  case class Remove(deployment: Deployment, cluster: DeploymentCluster, port: Port) extends RouterDriveMessage

}

class RouterDriverActor(driver: RouterDriver) extends Actor with ActorLogging with ActorSupport with ReplyActor with FutureSupportNotification with ActorExecutionContextProvider with RouterDriverNotificationProvider {

  implicit val timeout = RouterDriverActor.timeout

  override protected def requestType: Class[_] = classOf[RouterDriveMessage]

  override protected def errorRequest(request: Any): RequestError = UnsupportedRouterDriverRequest(request)

  def reply(request: Any) = try {
    request match {
      case All => offLoad(driver.all, classOf[RouterResponseError])
      case Update(deployment, cluster, port) => offLoad(driver.update(deployment, cluster, port), classOf[RouterResponseError])
      case Remove(deployment, cluster, port) => offLoad(driver.remove(deployment, cluster, port), classOf[RouterResponseError])
      case _ => unsupported(request)
    }
  } catch {
    case e: Exception => e
  }
}
