package io.vamp.core.container_driver

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.common.notification.Notification
import io.vamp.common.vitals.InfoRequest
import io.vamp.core.container_driver.notification.{ ContainerDriverNotificationProvider, ContainerResponseError, UnsupportedContainerDriverRequest }
import io.vamp.core.model.artifact.{ Deployment, DeploymentCluster, DeploymentService }
import io.vamp.core.pulse.notification.PulseFailureNotifier

import scala.concurrent.duration._

object ContainerDriverActor {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.core.container-driver.response-timeout").seconds)

  trait ContainerDriveMessage

  object All extends ContainerDriveMessage

  case class Deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) extends ContainerDriveMessage

  case class Undeploy(deployment: Deployment, service: DeploymentService) extends ContainerDriveMessage

}

class ContainerDriverActor(driver: ContainerDriver) extends PulseFailureNotifier with CommonSupportForActors with ContainerDriverNotificationProvider {

  import io.vamp.core.container_driver.ContainerDriverActor._

  implicit val timeout = ContainerDriverActor.timeout

  override def errorNotificationClass = classOf[ContainerResponseError]

  def receive = {
    case InfoRequest ⇒ reply(driver.info)
    case All ⇒ reply(driver.all)
    case Deploy(deployment, cluster, service, update) ⇒ reply(driver.deploy(deployment, cluster, service, update))
    case Undeploy(deployment, service) ⇒ reply(driver.undeploy(deployment, service))
    case any ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)
}

