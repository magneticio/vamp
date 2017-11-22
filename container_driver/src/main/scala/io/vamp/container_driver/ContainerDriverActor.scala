package io.vamp.container_driver

import akka.actor.ActorRef
import io.vamp.common.Config
import io.vamp.common.akka._
import io.vamp.common.http.HttpClient
import io.vamp.common.notification.Notification
import io.vamp.container_driver.notification.{ContainerDriverNotificationProvider, ContainerResponseError}
import io.vamp.model.artifact.{Deployment, _}
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.pulse.notification.PulseFailureNotifier
import io.vamp.common.Id
import io.vamp.persistence.refactor.serialization.VampJsonFormats

import scala.concurrent.Future

object ContainerDriverActor {

  lazy val timeout = Config.timeout("vamp.container-driver.response-timeout")

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

sealed trait ContainerRuntime {
  def containers: Option[Containers]
  def health: Option[Health]
  def equalHealthChecks: Boolean
}

/**
 * @param equalHealthChecks compares the health checks of the container service with the vamp service,
 *                          boolean value due to healthChecks being able only to transform one way
 */
case class ContainerService(
  deployment:        Deployment,
  service:           DeploymentService,
  containers:        Option[Containers],
  health:            Option[Health]     = None,
  equalHealthChecks: Boolean            = true
) extends ContainerRuntime

case class ContainerWorkflow(
  workflow:          Workflow,
  containers:        Option[Containers],
  health:            Option[Health]     = None,
  equalHealthChecks: Boolean            = true
) extends ContainerRuntime

case class Containers(scale: DefaultScale, instances: List[ContainerInstance])

case class ContainerInstance(name: String, host: String, ports: List[Int], deployed: Boolean)

case class ContainerInfo(`type`: String, container: Any)

trait ContainerDriverActor extends PulseFailureNotifier with CommonSupportForActors with ContainerDriverNotificationProvider with VampJsonFormats {

  implicit val timeout = ContainerDriverActor.timeout()

  lazy protected val httpClient = new HttpClient

  lazy val gatewayServiceIp = Config.string("vamp.gateway-driver.host")()

  protected def deployedGateways(gateways: List[Gateway]): Future[Any] = {
    gateways.filter {
      gateway ⇒ gateway.service.isEmpty && gateway.port.assigned
    } foreach {
      gateway ⇒ setGatewayService(gateway, gatewayServiceIp, gateway.port.number)
    }
    Future.successful(true)
  }

  protected def setGatewayService(gateway: Gateway, host: String, port: Int) = {
    VampPersistence().update(Id[Gateway](gateway.name), (g: Gateway) => g.copy(
      service = Some(GatewayService(host, gateway.port.copy(number = port) match { case p ⇒ p.copy(value = Option(p.toValue)) }))
    ))
  }

  override def errorNotificationClass = classOf[ContainerResponseError]

  override def failure(failure: Any, `class`: Class[_ <: Notification] = errorNotificationClass) = super[PulseFailureNotifier].failure(failure, `class`)
}

