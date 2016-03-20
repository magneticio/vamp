package io.vamp.container_driver

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider, ContainerResponseError, UnsupportedContainerDriverRequest }
import io.vamp.model.artifact.{ Deployment, DeploymentCluster, DeploymentService }
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.duration._

object ContainerDriverActor {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.container-driver.response-timeout").seconds)

  trait ContainerDriveMessage

  object All extends ContainerDriveMessage

  case class Deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) extends ContainerDriveMessage

  case class Undeploy(deployment: Deployment, service: DeploymentService) extends ContainerDriveMessage

}

case class ContainerInfo(`type`: String, container: Any)

class ContainerDriverActor(`type`: String, driver: ContainerDriver) extends PulseFailureNotifier with CommonSupportForActors with ContainerDriverNotificationProvider {

  import io.vamp.container_driver.ContainerDriverActor._

  implicit val timeout = ContainerDriverActor.timeout

  override def errorNotificationClass = classOf[ContainerResponseError]

  def receive = {
    case InfoRequest ⇒ reply(driver.info.map(info ⇒ ContainerInfo(`type`, info)))
    case All ⇒ reply(driver.all)
    case Deploy(deployment, cluster, service, update) ⇒ reply(driver.deploy(deployment, cluster, service, update))
    case Undeploy(deployment, service) ⇒ reply(driver.undeploy(deployment, service))
    case any ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)
}

