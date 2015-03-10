package io.magnetic.vamp_core.container_driver

import _root_.io.magnetic.vamp_common.akka._
import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_core.container_driver.ContainerDriverActor.{All, ContainerDriveMessage, Deploy, Undeploy}
import io.magnetic.vamp_core.container_driver.notification.{ContainerDriverNotificationProvider, ContainerResponseError, UnsupportedContainerDriverRequest}
import io.magnetic.vamp_core.model.artifact._

import scala.concurrent.duration._

object ContainerDriverActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("deployment.container.response.timeout").seconds)

  def props(args: Any*): Props = Props(classOf[ContainerDriverActor], args: _*)

  trait ContainerDriveMessage

  object All extends ContainerDriveMessage

  case class Deploy(deployment: Deployment, service: DeploymentService) extends ContainerDriveMessage

  case class Undeploy(deployment: Deployment, service: DeploymentService) extends ContainerDriveMessage

}

class ContainerDriverActor(driver: ContainerDriver) extends Actor with ActorLogging with ActorSupport with ReplyActor with FutureSupportNotification with ActorExecutionContextProvider with ContainerDriverNotificationProvider {

  implicit val timeout = ContainerDriverActor.timeout

  override protected def requestType: Class[_] = classOf[ContainerDriveMessage]

  override protected def errorRequest(request: Any): RequestError = UnsupportedContainerDriverRequest(request)

  def reply(request: Any) = try {
    request match {
      case All => offLoad(driver.all, classOf[ContainerResponseError])
      case Deploy(deployment, DeploymentService(_, breed: DefaultBreed, Some(scale: DefaultScale), _, _)) => offLoad(driver.deploy(deployment, breed, scale), classOf[ContainerResponseError])
      case Undeploy(deployment, service) => offLoad(driver.undeploy(deployment, service.breed), classOf[ContainerResponseError])
      case _ => unsupported(request)
    }
  } catch {
    case e: Exception => e
  }
}

