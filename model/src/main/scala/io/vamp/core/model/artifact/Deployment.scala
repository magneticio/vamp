package io.vamp.core.model.artifact

import java.time.OffsetDateTime

import io.vamp.common.notification.Notification

object DeploymentService {

  trait State {
    def startedAt: OffsetDateTime
  }

  case class ReadyForDeployment(startedAt: OffsetDateTime = OffsetDateTime.now()) extends State

  case class Deployed(startedAt: OffsetDateTime = OffsetDateTime.now()) extends State

  case class ReadyForUndeployment(startedAt: OffsetDateTime = OffsetDateTime.now()) extends State

  case class Error(notification: Notification, startedAt: OffsetDateTime = OffsetDateTime.now()) extends State

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
    case -1 => None
    case clusterIndex =>
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
    case -1 => None
    case clusterIndex =>
      val cluster = reference.substring(0, clusterIndex)
      val name = reference.substring(clusterIndex + 1)
      Some(NoGroupReference(cluster, name))
  }
}

case class NoGroupReference(cluster: String, name: String) extends ValueReference {
  def asTraitReference(group: String) = TraitReference(cluster, group, name).toString

  lazy val reference = s"$cluster${NoGroupReference.delimiter}$name"
}