package io.vamp.model.artifact

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
}

case class DefaultBlueprint(name: String, clusters: List[Cluster], gateways: List[Gateway], environmentVariables: List[EnvironmentVariable]) extends AbstractBlueprint {
  lazy val traits = environmentVariables
}

case class BlueprintReference(name: String) extends Blueprint with Reference

object Dialect extends Enumeration {

  val Marathon, Docker = Value
}

abstract class AbstractCluster extends Artifact {

  val kind = "cluster"

  def services: List[AbstractService]

  def gateways: List[Gateway]

  def network: Option[String]

  def sla: Option[Sla]

  def dialects: Map[Dialect.Value, Any]

  def gatewayBy(portName: String): Option[Gateway] = gateways.find(_.port.name == portName)
}

case class Cluster(name: String, services: List[Service], gateways: List[Gateway], network: Option[String] = None, sla: Option[Sla] = None, dialects: Map[Dialect.Value, Any] = Map()) extends AbstractCluster

abstract class AbstractService {

  val kind = "service"

  def breed: Breed

  def environmentVariables: List[EnvironmentVariable]

  def scale: Option[Scale]

  def arguments: List[Argument]

  def network: Option[String]

  def dialects: Map[Dialect.Value, Any]
}

case class Service(breed: Breed, environmentVariables: List[EnvironmentVariable], scale: Option[Scale], arguments: List[Argument], network: Option[String] = None, dialects: Map[Dialect.Value, Any] = Map()) extends AbstractService

trait Scale extends Artifact {
  val kind = "scale"
}

case class ScaleReference(name: String) extends Reference with Scale

object DefaultScale {
  def apply(instances: Int = 0): DefaultScale = DefaultScale(name = "", cpu = Quantity(0.0), memory = MegaByte(0.0), instances)
}

case class DefaultScale(name: String, cpu: Quantity, memory: MegaByte, instances: Int) extends Scale
