package io.vamp.core.model.artifact

abstract class Blueprint extends Artifact

trait AbstractBlueprint extends Blueprint {
  def name: String

  def clusters: List[AbstractCluster]

  def endpoints: List[Port]

  def environmentVariables: List[EnvironmentVariable]

  def traits: List[Trait]
}

case class DefaultBlueprint(name: String, clusters: List[Cluster], endpoints: List[Port], environmentVariables: List[EnvironmentVariable]) extends AbstractBlueprint {
  lazy val traits = environmentVariables
}

case class BlueprintReference(name: String) extends Blueprint with Reference


abstract class AbstractCluster extends Artifact {
  def services: List[AbstractService]

  def sla: Option[Sla]

  def dialects: Map[String, Any]
}

case class Cluster(name: String, services: List[Service], sla: Option[Sla], dialects: Map[String, Any] = Map()) extends AbstractCluster


abstract class AbstractService {
  def breed: Breed

  def scale: Option[Scale]

  def routing: Option[Routing]

  def dialects: Map[String, Any]
}

case class Service(breed: Breed, scale: Option[Scale], routing: Option[Routing], dialects: Map[String, Any] = Map()) extends AbstractService


trait Scale extends Artifact

case class ScaleReference(name: String) extends Reference with Scale

case class DefaultScale(name: String, cpu: Double, memory: Double, instances: Int) extends Scale


trait Routing extends Artifact

case class RoutingReference(name: String) extends Reference with Routing

case class DefaultRouting(name: String, weight: Option[Int], filters: List[Filter]) extends Routing


trait Filter extends Artifact

case class FilterReference(name: String) extends Reference with Filter

case class DefaultFilter(name: String, condition: String) extends Filter
