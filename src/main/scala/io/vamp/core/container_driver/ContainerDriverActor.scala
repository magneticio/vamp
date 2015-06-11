package io.vamp.core.container_driver

import _root_.io.vamp.common.akka._
import akka.actor.Props
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.vitals.InfoRequest
import io.vamp.core.container_driver.notification.{ContainerDriverNotificationProvider, ContainerResponseError, UnsupportedContainerDriverRequest}
import io.vamp.core.model.artifact.{Deployment, DeploymentCluster, DeploymentService}

import scala.concurrent.duration._

object ContainerDriverActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.core.container-driver.response-timeout").seconds)

  def props(args: Any*): Props = Props(classOf[ContainerDriverActor], args: _*)

  trait ContainerDriveMessage

  object All extends ContainerDriveMessage

  case class Deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) extends ContainerDriveMessage

  case class Undeploy(deployment: Deployment, service: DeploymentService) extends ContainerDriveMessage

}

class ContainerDriverActor(driver: ContainerDriver) extends CommonReplyActor with ContainerDriverNotificationProvider {

  import io.vamp.core.container_driver.ContainerDriverActor._

  implicit val timeout = ContainerDriverActor.timeout

  override protected def requestType: Class[_] = classOf[ContainerDriveMessage]

  override protected def errorRequest(request: Any): RequestError = UnsupportedContainerDriverRequest(request)

  def reply(request: Any) = try {
    request match {
      case InfoRequest => offload(driver.info, classOf[ContainerResponseError])
      case All => offload(driver.all, classOf[ContainerResponseError])
      case Deploy(deployment, cluster, service, update) => offload(driver.deploy(deployment, cluster, service, update), classOf[ContainerResponseError])
      case Undeploy(deployment, service) => offload(driver.undeploy(deployment, service), classOf[ContainerResponseError])
      case _ => unsupported(request)
    }
  } catch {
    case e: Exception => exception(ContainerResponseError(e))
  }
}

