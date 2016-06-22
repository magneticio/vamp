package io.vamp.model.artifact

import java.time.OffsetDateTime

import io.vamp.common.notification.Notification
import io.vamp.model.artifact.DeploymentService.State.Intention.StateIntentionType
import io.vamp.model.artifact.DeploymentService.State.Step.{ Done, Initiated }

import scala.language.implicitConversions
import scala.language.postfixOps

object DeploymentService {

  object State {

    object Intention extends Enumeration {
      type StateIntentionType = Value
      val Deploy, Undeploy = Value
    }

    sealed trait Step {
      def since: OffsetDateTime

      def name: String = {
        val clazz = getClass.toString
        clazz.substring(clazz.lastIndexOf('$') + 1)
      }
    }

    object Step {

      case class Initiated(since: OffsetDateTime = OffsetDateTime.now()) extends Step

      case class Update(since: OffsetDateTime = OffsetDateTime.now()) extends Step

      case class Done(since: OffsetDateTime = OffsetDateTime.now()) extends Step

      case class Failure(notification: Notification, since: OffsetDateTime = OffsetDateTime.now()) extends Step

    }

  }

  case class State(intention: StateIntentionType, step: State.Step = Initiated(), since: OffsetDateTime = OffsetDateTime.now()) {
    def isDone = step.isInstanceOf[Done]

    def isDeployed = intention == State.Intention.Deploy && isDone

    def isUndeployed = intention == State.Intention.Undeploy && isDone
  }

  implicit def step2state(intention: StateIntentionType): State = State(intention)
}

trait DeploymentState {
  def state: DeploymentService.State
}

object Deployment {
  def gatewayNameFor(deployment: Deployment, gateway: Gateway) = GatewayPath(deployment.name :: gateway.port.name :: Nil).normalized
}

case class Deployment(
    name: String,
    clusters: List[DeploymentCluster],
    gateways: List[Gateway],
    ports: List[Port],
    environmentVariables: List[EnvironmentVariable],
    hosts: List[Host]) extends AbstractBlueprint with Lookup {
  lazy val traits = ports ++ environmentVariables ++ hosts
}

object DeploymentCluster {
  def gatewayNameFor(deployment: Deployment, cluster: DeploymentCluster, port: Port) = GatewayPath(deployment.name :: cluster.name :: port.name :: Nil).normalized
}

case class DeploymentCluster(
    name: String,
    services: List[DeploymentService],
    gateways: List[Gateway],
    sla: Option[Sla],
    dialects: Map[Dialect.Value, Any] = Map()) extends AbstractCluster {

  def portBy(name: String): Option[Int] = {
    gateways.find { gateway ⇒ GatewayPath(gateway.name).segments.last == name } map { _.port.number }
  }

  def serviceBy(name: String): Option[GatewayService] = {
    gateways.find { gateway ⇒ GatewayPath(gateway.name).segments.last == name } flatMap { _.service }
  }

  def route(service: DeploymentService, portName: String, short: Boolean = false): Option[DefaultRoute] = {
    gateways.find(_.port.name == portName).flatMap(routing ⇒ routing.routes.find { route ⇒
      route.path.segments match {
        case s :: Nil if short                 ⇒ s == service.breed.name
        case _ :: _ :: s :: _ :: Nil if !short ⇒ s == service.breed.name
        case _                                 ⇒ false
      }
    }).asInstanceOf[Option[DefaultRoute]]
  }
}

case class DeploymentService(
  state: DeploymentService.State,
  breed: DefaultBreed,
  environmentVariables: List[EnvironmentVariable],
  scale: Option[DefaultScale],
  instances: List[DeploymentInstance],
  arguments: List[Argument],
  dependencies: Map[String, String] = Map(),
  dialects: Map[Dialect.Value, Any] = Map()) extends AbstractService with DeploymentState

case class DeploymentInstance(name: String, host: String, ports: Map[String, Int], deployed: Boolean) extends Artifact

object Host {
  val host = "host"
}

case class Host(name: String, value: Option[String]) extends Trait {
  def alias = None
}

object HostReference {

  val delimiter = TraitReference.delimiter

  def referenceFor(reference: String): Option[HostReference] = reference.indexOf(delimiter) match {
    case -1 ⇒ None
    case clusterIndex ⇒
      val cluster = reference.substring(0, clusterIndex)
      val name = reference.substring(clusterIndex + 1)
      if (name == Host.host) Some(HostReference(cluster)) else None
  }
}

case class HostReference(cluster: String) extends ValueReference {
  def asTraitReference = TraitReference(cluster, TraitReference.Hosts, Host.host).toString

  lazy val reference = s"$cluster${HostReference.delimiter}${Host.host}"
}

object NoGroupReference {

  val delimiter = TraitReference.delimiter

  def referenceFor(reference: String): Option[NoGroupReference] = reference.indexOf(delimiter) match {
    case -1 ⇒ None
    case clusterIndex ⇒
      val cluster = reference.substring(0, clusterIndex)
      val name = reference.substring(clusterIndex + 1)
      Some(NoGroupReference(cluster, name))
  }
}

case class NoGroupReference(cluster: String, name: String) extends ValueReference {
  def asTraitReference(group: String) = TraitReference(cluster, group, name).toString

  lazy val reference = s"$cluster${NoGroupReference.delimiter}$name"
}