package io.vamp.container_driver

import akka.actor.ActorRef
import akka.util.Timeout
import io.vamp.common.akka._
import io.vamp.common.http.HttpClient
import io.vamp.common.notification.{ ErrorNotification, Notification }
import io.vamp.common.{ Config, ConfigMagnet }
import io.vamp.container_driver.notification.{ ContainerDriverNotificationProvider, ContainerResponseError }
import io.vamp.model.artifact.{ Deployment, _ }
import io.vamp.persistence.PersistenceActor
import io.vamp.pulse.notification.PulseFailureNotifier

import scala.concurrent.Future

object ContainerDriverActor {

  lazy val timeout: ConfigMagnet[Timeout] = Config.timeout("vamp.container-driver.response-timeout")

  case class DeploymentServices(deployment: Deployment, services: List[DeploymentService])

  //

  sealed trait ContainerDriveMessage

  case class Get(deploymentServices: List[DeploymentServices]) extends ContainerDriveMessage

  case class Deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) extends ContainerDriveMessage

  case class Undeploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) extends ContainerDriveMessage

  case class DeployedGateways(gateway: List[Gateway]) extends ContainerDriveMessage

  case class GetWorkflow(workflow: Workflow, sender: ActorRef) extends ContainerDriveMessage

  case class DeployWorkflow(workflow: Workflow, update: Boolean) extends ContainerDriveMessage

  case class UndeployWorkflow(workflow: Workflow) extends ContainerDriveMessage

}

/**
 * Compares the checks (e.g. health) of the container service with the Vamp service,
 * if check is false than service update may be triggered.
 */
case class ContainerServiceEquality(
  breed:     Boolean = true,
  traits:    Boolean = true,
  arguments: Boolean = true,
  health:    Boolean = true,
  dialect:   Boolean = true
)

sealed trait ContainerRuntime {
  def containers: Option[Containers]

  def health: Option[Health]

  def equality: ContainerServiceEquality
}

case class ContainerService(
  deployment: Deployment,
  service:    DeploymentService,
  containers: Option[Containers],
  health:     Option[Health]           = None,
  equality:   ContainerServiceEquality = ContainerServiceEquality()
) extends ContainerRuntime

case class ContainerWorkflow(
  workflow:   Workflow,
  containers: Option[Containers],
  health:     Option[Health]           = None,
  equality:   ContainerServiceEquality = ContainerServiceEquality()
) extends ContainerRuntime

case class Containers(scale: DefaultScale, instances: List[ContainerInstance])

case class ContainerInstance(name: String, host: String, ports: List[Int], deployed: Boolean)

case class ContainerInfo(`type`: String, container: Any)

trait ContainerDriverActor extends PulseFailureNotifier with CommonSupportForActors with ContainerDriverNotificationProvider {

  implicit val timeout: Timeout = ContainerDriverActor.timeout()

  lazy protected val httpClient = new HttpClient

  lazy val gatewayServiceIp: String = Config.string("vamp.gateway-driver.host")()

  protected def deployedGateways(gateways: List[Gateway]): Future[Any] = {
    gateways.filter {
      gateway ⇒ gateway.service.isEmpty && gateway.port.assigned
    } foreach {
      gateway ⇒ setGatewayService(gateway, gatewayServiceIp, gateway.port.number)
    }
    Future.successful(true)
  }

  protected def setGatewayService(gateway: Gateway, host: String, port: Int): Unit = {
    IoC.actorFor[PersistenceActor].forward(PersistenceActor.CreateGatewayServiceAddress(gateway, host, port))
  }

  override def errorNotificationClass: Class[_ <: ErrorNotification] = classOf[ContainerResponseError]

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass): Exception = super[PulseFailureNotifier].failure(failure, `class`)
}
