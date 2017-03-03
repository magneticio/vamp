package io.vamp.model.artifact

import io.vamp.common.{ Artifact, Reference }
import io.vamp.model.reader.{ MegaByte, Quantity }

object Blueprint {
  val kind = "blueprint"
}

abstract class Blueprint extends Artifact {
  val kind = Blueprint.kind
}

trait AbstractBlueprint extends Blueprint {
  def name: String

  def clusters: List[AbstractCluster]

  def gateways: List[Gateway]

  def environmentVariables: List[EnvironmentVariable]

  def traits: List[Trait]

  def dialects: Map[String, Any]
}

case class DefaultBlueprint(
    name:                 String,
    metadata:             Map[String, Any],
    clusters:             List[Cluster],
    gateways:             List[Gateway],
    environmentVariables: List[EnvironmentVariable],
    dialects:             Map[String, Any]          = Map()
) extends AbstractBlueprint {
  lazy val traits = environmentVariables
}

case class BlueprintReference(name: String) extends Blueprint with Reference

abstract class AbstractCluster extends Artifact {

  val kind = "cluster"

  def services: List[AbstractService]

  def gateways: List[Gateway]

  def network: Option[String]

  def sla: Option[Sla]

  def dialects: Map[String, Any]

  def gatewayBy(portName: String): Option[Gateway] = gateways.find(_.port.name == portName)

  def healthChecks: Option[List[HealthCheck]]
}

case class Cluster(
  name:         String,
  metadata:     Map[String, Any],
  services:     List[Service],
  gateways:     List[Gateway],
  healthChecks: Option[List[HealthCheck]],
  network:      Option[String]            = None,
  sla:          Option[Sla]               = None,
  dialects:     Map[String, Any]          = Map()
) extends AbstractCluster

abstract class AbstractService {

  val kind = "service"

  def breed: Breed

  def environmentVariables: List[EnvironmentVariable]

  def scale: Option[Scale]

  def arguments: List[Argument]

  /** A service can contain zero or many health checks that will get created when the Blueprints gets deployed */
  def healthChecks: Option[List[HealthCheck]]

  def network: Option[String]

  def dialects: Map[String, Any]

  def health: Option[Health]
}

case class Service(
  breed:                Breed,
  environmentVariables: List[EnvironmentVariable],
  scale:                Option[Scale],
  arguments:            List[Argument],
  healthChecks:         Option[List[HealthCheck]],
  network:              Option[String]            = None,
  dialects:             Map[String, Any]          = Map(),
  health:               Option[Health]            = None
) extends AbstractService

trait Scale extends Artifact {
  val kind = "scale"
}

case class ScaleReference(name: String) extends Reference with Scale

object DefaultScale {

  def apply(cpu: Quantity, memory: MegaByte, instances: Int): DefaultScale = DefaultScale(name = "", metadata = Map(), cpu, memory, instances)

  def apply(instances: Int = 0): DefaultScale = DefaultScale(name = "", metadata = Map(), cpu = Quantity(0.0), memory = MegaByte(0.0), instances)
}

case class DefaultScale(name: String, metadata: Map[String, Any], cpu: Quantity, memory: MegaByte, instances: Int) extends Scale

/**
 * Representation of the Health retrieved from a Deployment.
 * @param staged number of instances in a staged state.
 * @param running number of instances in a running state.
 * @param healthy number of instances in a healthy state.
 * @param unhealthy number of instances in an unhealthy state.
 */
case class Health(staged: Int, running: Int, healthy: Int, unhealthy: Int)
