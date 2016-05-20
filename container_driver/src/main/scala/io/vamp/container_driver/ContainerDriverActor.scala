package io.vamp.container_driver

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.common.notification.Notification
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider, ContainerResponseError }
import io.vamp.model.artifact._
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.duration._

object ContainerDriverActor {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.container-driver.response-timeout").seconds)

  trait ContainerDriveMessage

  object All extends ContainerDriveMessage

  case class Deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) extends ContainerDriveMessage

  case class Undeploy(deployment: Deployment, service: DeploymentService) extends ContainerDriveMessage

  case class DeployedGateways(gateway: List[Gateway]) extends ContainerDriveMessage

}

case class ContainerService(matching: (Deployment, Breed) ⇒ Boolean, scale: DefaultScale, instances: List[ContainerInstance])

case class ContainerInstance(name: String, host: String, ports: List[Int], deployed: Boolean)

case class ContainerInfo(`type`: String, container: Any)

trait ContainerDriverActor extends PulseFailureNotifier with CommonSupportForActors with ContainerDriverNotificationProvider {

  implicit val timeout = ContainerDriverActor.timeout

  override def errorNotificationClass = classOf[ContainerResponseError]

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)
}

