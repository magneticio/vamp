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

  object GetRoutingGroups extends ContainerDriveMessage

  case class Get(deploymentServices: List[DeploymentServices], equalityRequest: ServiceEqualityRequest) extends ContainerDriveMessage

  case class Deploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, update: Boolean) extends ContainerDriveMessage

  case class Undeploy(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService) extends ContainerDriveMessage

  case class DeployedGateways(gateway: List[Gateway]) extends ContainerDriveMessage

  case class GetWorkflow(workflow: Workflow, sender: ActorRef) extends ContainerDriveMessage

  case class DeployWorkflow(workflow: Workflow, update: Boolean) extends ContainerDriveMessage

  case class UndeployWorkflow(workflow: Workflow) extends ContainerDriveMessage

}

/**
 * Comparison of model (intention) and container (actual) services.
 * Depending on configuration, any difference may lead to service (runtime) update.
 */
trait ServiceEquality {
  def deployable: Boolean

  def ports: Boolean

  def environmentVariables: Boolean

  def health: Boolean
}

case class ServiceEqualityRequest(
  deployable:           Boolean,
  ports:                Boolean,
  environmentVariables: Boolean,
  health:               Boolean
) extends ServiceEquality

case class ServiceEqualityResponse(
  deployable:           Boolean = true,
  ports:                Boolean = true,
  environmentVariables: Boolean = true,
  health:               Boolean = true
) extends ServiceEquality

sealed trait ContainerRuntime {
  def containers: Option[Containers]

  def health: Option[Health]
}

case class ContainerService(
  deployment: Deployment,
  service:    DeploymentService,
  containers: Option[Containers],
  health:     Option[Health]          = None,
  equality:   ServiceEqualityResponse = ServiceEqualityResponse()
) extends ContainerRuntime

case class ContainerWorkflow(
  workflow:   Workflow,
  containers: Option[Containers],
  health:     Option[Health]     = None
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

case class RoutingGroup(
  name:      String,
  kind:      String,
  namespace: String,
  labels:    Map[String, String],
  image:     Option[String],
  instances: List[RoutingInstance]
)

case class RoutingInstance(ip: String, ports: Map[Int, Int])
