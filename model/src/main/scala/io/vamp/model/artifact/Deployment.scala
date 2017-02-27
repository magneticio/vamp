package io.vamp.model.artifact

import java.time.OffsetDateTime

import io.vamp.common.notification.Notification
import io.vamp.model.artifact.DeploymentService.Status.Intention.StatusIntentionType
import io.vamp.model.artifact.DeploymentService.Status.Phase.{ Done, Initiated }

import scala.language.implicitConversions

object DeploymentService {

  object Status {

    object Intention extends Enumeration {
      type StatusIntentionType = Value
      val Deployment, Undeployment = Value
    }

    sealed trait Phase {
      def since: OffsetDateTime

      def name: String = {
        val clazz = getClass.toString
        clazz.substring(clazz.lastIndexOf('$') + 1)
      }
    }

    object Phase {

      case class Initiated(since: OffsetDateTime = OffsetDateTime.now()) extends Phase

      case class Updating(since: OffsetDateTime = OffsetDateTime.now()) extends Phase

      case class Done(since: OffsetDateTime = OffsetDateTime.now()) extends Phase

      case class Failed(notification: Notification, since: OffsetDateTime = OffsetDateTime.now()) extends Phase

    }

  }

  case class Status(intention: StatusIntentionType, phase: Status.Phase = Initiated(), since: OffsetDateTime = OffsetDateTime.now()) {
    def isDone = phase.isInstanceOf[Done]

    def isDeployed = intention == Status.Intention.Deployment && isDone

    def isUndeployed = intention == Status.Intention.Undeployment && isDone
  }

  implicit def intention2status(intention: StatusIntentionType): Status = Status(intention)
}

object Deployment {
  val kind = "deployment"

  def gatewayNameFor(deployment: Deployment, gateway: Gateway) = GatewayPath(deployment.name :: gateway.port.name :: Nil).normalized
}

case class Deployment(
    name:                 String,
    metadata:             Map[String, Any],
    clusters:             List[DeploymentCluster],
    gateways:             List[Gateway],
    ports:                List[Port],
    environmentVariables: List[EnvironmentVariable],
    hosts:                List[Host],
    dialects:             Map[String, Any]          = Map()
) extends AbstractBlueprint with Lookup {

  override val kind = Deployment.kind

  lazy val traits = ports ++ environmentVariables ++ hosts

  def service(breed: Breed): Option[DeploymentService] = {
    clusters.flatMap { cluster ⇒ cluster.services } find { service ⇒ service.breed.name == breed.name }
  }
}

object DeploymentCluster {
  def gatewayNameFor(deployment: Deployment, cluster: DeploymentCluster, port: Port) = GatewayPath(deployment.name :: cluster.name :: port.name :: Nil).normalized
}

case class DeploymentCluster(
    name:         String,
    metadata:     Map[String, Any],
    services:     List[DeploymentService],
    gateways:     List[Gateway],
    healthChecks: Option[List[HealthCheck]],
    network:      Option[String],
    sla:          Option[Sla],
    dialects:     Map[String, Any]          = Map()
) extends AbstractCluster {

  def portBy(name: String): Option[Int] = {
    gateways.find { gateway ⇒ GatewayPath(gateway.name).segments.last == name } map {
      _.port.number
    }
  }

  def serviceBy(name: String): Option[GatewayService] = {
    gateways.find { gateway ⇒ GatewayPath(gateway.name).segments.last == name } flatMap {
      _.service
    }
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
  status:               DeploymentService.Status,
  breed:                DefaultBreed,
  environmentVariables: List[EnvironmentVariable],
  scale:                Option[DefaultScale],
  instances:            List[Instance],
  arguments:            List[Argument],
  healthChecks:         Option[List[HealthCheck]],
  network:              Option[String],
  dependencies:         Map[String, String]       = Map(),
  dialects:             Map[String, Any]          = Map(),
  health:               Option[Health]            = None
) extends AbstractService

case class Instance(name: String, host: String, ports: Map[String, Int], deployed: Boolean) extends Artifact {
  val kind = "instance"

  val metadata = Map()
}

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

case class HostReference(cluster: String) extends ClusterReference {
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

case class NoGroupReference(cluster: String, name: String) extends ClusterReference {
  def asTraitReference(group: String) = TraitReference(cluster, group, name).toString

  lazy val reference = s"$cluster${NoGroupReference.delimiter}$name"
}