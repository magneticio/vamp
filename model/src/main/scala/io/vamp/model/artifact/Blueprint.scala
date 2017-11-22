package io.vamp.model.artifact

import java.time.OffsetDateTime

import io.vamp.model.artifact.DeploymentService.Status.Intention.StatusIntentionType
import io.vamp.model.artifact.DeploymentService.Status.Phase.{Done, Initiated}
import io.vamp.common.{ Artifact, Reference, RootAnyMap }
import io.vamp.model.reader.{ MegaByte, Quantity }

object Blueprint {
  val kind: String = "blueprints"
}

sealed abstract class Blueprint extends Artifact {
  val kind: String = Blueprint.kind
}

sealed trait AbstractBlueprint extends Blueprint {
  def name: String

  def clusters: List[AbstractCluster]

  def gateways: List[Gateway]

  def environmentVariables: List[EnvironmentVariable]

  def traits: List[Trait]

  def dialects: RootAnyMap
}

case class DefaultBlueprint(
    name:                 String,
    metadata:             RootAnyMap,
    clusters:             List[Cluster],
    gateways:             List[Gateway],
    environmentVariables: List[EnvironmentVariable],
    dialects:             RootAnyMap          = RootAnyMap.empty
) extends AbstractBlueprint {
  lazy val traits: List[Trait] = environmentVariables
}

case class BlueprintReference(name: String) extends Blueprint with Reference

sealed abstract class AbstractCluster extends Artifact {

  val kind: String = "clusters"

  def services: List[AbstractService]

  def gateways: List[Gateway]

  def network: Option[String]

  def sla: Option[Sla]

  def dialects: RootAnyMap

  def gatewayBy(portName: String): Option[Gateway] = gateways.find(_.port.name == portName)

  def healthChecks: Option[List[HealthCheck]]
}

case class Cluster(
  name:         String,
  metadata:     RootAnyMap,
  services:     List[Service],
  gateways:     List[Gateway],
  healthChecks: Option[List[HealthCheck]],
  network:      Option[String]            = None,
  sla:          Option[Sla]               = None,
  dialects:     RootAnyMap                = RootAnyMap.empty
) extends AbstractCluster

object DeploymentCluster {
  def gatewayNameFor(deployment: Deployment, cluster: DeploymentCluster, port: Port): String = GatewayPath(deployment.name :: cluster.name :: port.name :: Nil).normalized
}

case class DeploymentCluster(
                              name:         String,
                              metadata:     RootAnyMap,
                              services:     List[DeploymentService],
                              gateways:     List[Gateway],
                              healthChecks: Option[List[HealthCheck]],
                              network:      Option[String],
                              sla:          Option[Sla],
                              dialects:     RootAnyMap          = RootAnyMap.empty
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


sealed abstract class AbstractService {

  val kind: String = "services"

  def breed: Breed

  def environmentVariables: List[EnvironmentVariable]

  def scale: Option[Scale]

  def arguments: List[Argument]

  /** A service can contain zero or many health checks that will get created when the Blueprints gets deployed */
  def healthChecks: Option[List[HealthCheck]]

  def network: Option[String]

  def dialects: RootAnyMap

  def health: Option[Health]
}

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

      case class Failed(notificationMessage: String, since: OffsetDateTime = OffsetDateTime.now()) extends Phase

    }

  }

  case class Status(intention: StatusIntentionType, phase: Status.Phase = Initiated(), since: OffsetDateTime = OffsetDateTime.now()) {
    def isDone: Boolean = phase.isInstanceOf[Done]

    def isDeployed: Boolean = intention == Status.Intention.Deployment && isDone

    def isUndeployed: Boolean = intention == Status.Intention.Undeployment && isDone
  }

  implicit def intention2status(intention: StatusIntentionType): Status = Status(intention)
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
  dialects:             RootAnyMap                = RootAnyMap.empty,
  health:               Option[Health]            = None
) extends AbstractService

case class Service(
  breed:                Breed,
  environmentVariables: List[EnvironmentVariable],
  scale:                Option[Scale],
  arguments:            List[Argument],
  healthChecks:         Option[List[HealthCheck]],
  network:              Option[String]            = None,
  dialects:             RootAnyMap                = RootAnyMap.empty,
  health:               Option[Health]            = None
) extends AbstractService

object Scale {
  val kind: String = "scales"
}

sealed trait Scale extends Artifact {
  val kind: String = Scale.kind
}

case class ScaleReference(name: String) extends Reference with Scale

object DefaultScale {

  def apply(cpu: Quantity, memory: MegaByte, instances: Int): DefaultScale = DefaultScale(name = "", metadata = RootAnyMap.empty, cpu, memory, instances)

  def apply(instances: Int = 0): DefaultScale = DefaultScale(name = "", metadata = RootAnyMap.empty, cpu = Quantity(0.0), memory = MegaByte(0.0), instances)
}

case class DefaultScale(name: String, metadata: RootAnyMap, cpu: Quantity, memory: MegaByte, instances: Int) extends Scale

/**
 * Representation of the Health retrieved from a Deployment.
 * @param staged number of instances in a staged state.
 * @param running number of instances in a running state.
 * @param healthy number of instances in a healthy state.
 * @param unhealthy number of instances in an unhealthy state.
 */
case class Health(staged: Int, running: Int, healthy: Int, unhealthy: Int)
