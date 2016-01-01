package io.vamp.model.artifact

import io.vamp.model.reader.MegaByte

abstract class Blueprint extends Artifact

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
  def services: List[AbstractService]

  def routing: List[Gateway]

  def sla: Option[Sla]

  def dialects: Map[Dialect.Value, Any]

  def routingBy(portName: String): Option[Gateway] = routing.find(_.port.name == portName)
}

case class Cluster(name: String, services: List[Service], routing: List[Gateway], sla: Option[Sla], dialects: Map[Dialect.Value, Any] = Map()) extends AbstractCluster

abstract class AbstractService {
  def breed: Breed

  def environmentVariables: List[EnvironmentVariable]

  def scale: Option[Scale]

  def dialects: Map[Dialect.Value, Any]
}

case class Service(breed: Breed, environmentVariables: List[EnvironmentVariable], scale: Option[Scale], dialects: Map[Dialect.Value, Any] = Map()) extends AbstractService

trait Scale extends Artifact

case class ScaleReference(name: String) extends Reference with Scale

case class DefaultScale(name: String, cpu: Double, memory: MegaByte, instances: Int) extends Scale
