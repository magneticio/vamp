package io.magnetic.vamp_core.container_driver

import _root_.io.magnetic.vamp_common.akka._
import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.magnetic.marathon.client.Marathon
import io.magnetic.marathon.client.api._
import io.magnetic.vamp_core.container_driver.ContainerDriverActor.{All, ContainerDriveMessage, Deploy, Undeploy}
import io.magnetic.vamp_core.container_driver.notification.{ContainerDriverNotificationProvider, ContainerResponseError, UnsupportedContainerDriverRequest}
import io.magnetic.vamp_core.model.artifact.{AnonymousScale, DefaultBreed, Deployment}

import scala.concurrent.duration._

object ContainerDriverActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("deployment.container.response.timeout").seconds)

  def props(args: Any*): Props = Props(classOf[ContainerDriverActor], args: _*)

  trait ContainerDriveMessage

  object All extends ContainerDriveMessage

  case class Deploy(deployment: Deployment, service: ContainerService) extends ContainerDriveMessage

  case class Undeploy(deployment: Deployment, service: ContainerService) extends ContainerDriveMessage

}

class ContainerDriverActor(url: String) extends Actor with ActorLogging with ActorSupport with ReplyActor with FutureSupport with ActorExecutionContextProvider with ContainerDriverNotificationProvider {

  implicit val timeout = ContainerDriverActor.timeout

  override protected def requestType: Class[_] = classOf[ContainerDriveMessage]

  override protected def errorRequest(request: Any): RequestError = UnsupportedContainerDriverRequest(request)

  def reply(request: Any) = try {
    request match {
      case All => all
      case Deploy(deployment, ContainerService(name, Some(breed: DefaultBreed), Some(scale: AnonymousScale))) => deploy(deployment, breed, scale)
      case Undeploy(deployment, service) => undeploy(deployment, service)
      case _ => exception(errorRequest(request))
    }
  } catch {
    case e: Exception => e
  }

  private def all: List[ContainerService] = {
    offLoad(new Marathon(url).apps) match {
      case response: Apps =>
        response.apps.map { app =>
          ContainerService(app.id, None, None)
        }
      case any =>
        exception(ContainerResponseError(any))
        List[ContainerService]()
    }
  }

  private def deploy(deployment: Deployment, breed: DefaultBreed, scale: AnonymousScale) = {
    val docker = Docker(breed.deployable.name, "BRIDGE", Nil)
    val app = App(s"/${deployment.name}/${breed.name}", None, Nil, None, Map(), scale.instances, scale.cpu, scale.memory, 0, "", Nil, Nil, Nil, Nil, requirePorts = false, 0, Container("DOCKER", Nil, docker), Nil, Nil, UpgradeStrategy(0), "1", Nil, None, None, 0, 0, 0)
    new Marathon(url).createApp(app)
  }

  private def undeploy(deployment: Deployment, service: ContainerService) = {}
}
