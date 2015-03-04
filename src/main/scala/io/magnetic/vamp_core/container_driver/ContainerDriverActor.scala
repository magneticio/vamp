package io.magnetic.vamp_core.container_driver

import _root_.io.magnetic.vamp_common.akka._
import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_core.container_driver.ContainerDriverActor.{All, ContainerDriveMessage, Deploy, Undeploy}
import io.magnetic.vamp_core.container_driver.notification.{ContainerDriverNotificationProvider, UnsupportedContainerDriverRequest}

import scala.concurrent.duration._

object ContainerDriverActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("deployment.container.response.timeout").seconds)

  def props(args: Any*): Props = Props(classOf[ContainerDriverActor], args: _*)

  trait ContainerDriveMessage

  object All extends ContainerDriveMessage

  case class Deploy(service: ContainerService) extends ContainerDriveMessage

  case class Undeploy(service: ContainerService) extends ContainerDriveMessage

}

class ContainerDriverActor(url: String) extends Actor with ActorLogging with ActorSupport with ReplyActor with FutureSupport with ActorExecutionContextProvider with ContainerDriverNotificationProvider {

  override protected def requestType: Class[_] = classOf[ContainerDriveMessage]

  override protected def errorRequest(request: Any): RequestError = UnsupportedContainerDriverRequest(request)

  def reply(request: Any) = try {
    request match {
      case All => List[ContainerService]()
      case Deploy(service) =>
      case Undeploy(service) =>
      case _ => exception(errorRequest(request))
    }
  } catch {
    case e: Exception => e
  }
}
