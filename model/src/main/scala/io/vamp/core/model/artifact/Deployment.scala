package io.vamp.core.model.artifact

import java.time.OffsetDateTime

import io.vamp.common.notification.Notification
import io.vamp.core.model.artifact.DeploymentService.State.Intention.StateIntentionType
import io.vamp.core.model.artifact.DeploymentService.State.Step.{ Done, Initiated }

import scala.language.implicitConversions

object DeploymentService {

  object State {

    object Intention extends Enumeration {
      type StateIntentionType = Value
      val Deploy, Undeploy = Value
    }

    sealed trait Step {
      def since: OffsetDateTime
    }

    object Step {

      case class Initiated(since: OffsetDateTime = OffsetDateTime.now()) extends Step

      case class ContainerUpdate(since: OffsetDateTime = OffsetDateTime.now()) extends Step

      case class RouteUpdate(since: OffsetDateTime = OffsetDateTime.now()) extends Step

      case class Done(since: OffsetDateTime = OffsetDateTime.now()) extends Step

      case class Failure(notification: Notification, since: OffsetDateTime = OffsetDateTime.now()) extends Step

    }

  }

  case class State(intention: StateIntentionType, step: State.Step = Initiated(), since: OffsetDateTime = OffsetDateTime.now()) {
    def isDone = step.isInstanceOf[Done]

    def isDeployed = intention == State.Intention.Deploy && isDone
  }

  implicit def step2state(intention: StateIntentionType): State = State(intention)
}

trait DeploymentState {
  def state: DeploymentService.State
}

case class Deployment(name: String, clusters: List[DeploymentCluster], endpoints: List[Port], ports: List[Port], environmentVariables: List[EnvironmentVariable], hosts: List[Host]) extends AbstractBlueprint {
  lazy val traits = ports ++ environmentVariables ++ hosts
}

case class DeploymentCluster(name: String, services: List[DeploymentService], sla: Option[Sla], routes: Map[Int, Int] = Map(), dialects: Map[Dialect.Value, Any] = Map()) extends AbstractCluster

case class DeploymentService(state: DeploymentService.State, breed: DefaultBreed, environmentVariables: List[EnvironmentVariable], scale: Option[DefaultScale], routing: Option[DefaultRouting], servers: List[DeploymentServer], dependencies: Map[String, String] = Map(), dialects: Map[Dialect.Value, Any] = Map()) extends AbstractService with DeploymentState

case class DeploymentServer(name: String, host: String, ports: Map[Int, Int], deployed: Boolean) extends Artifact

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