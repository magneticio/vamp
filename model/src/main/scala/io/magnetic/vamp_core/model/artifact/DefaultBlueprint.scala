package io.magnetic.vamp_core.model.artifact

abstract class Blueprint extends Artifact

case class DefaultBlueprint(name: String, clusters: List[Cluster], endpoints: Map[Trait.Name, String], parameters: Map[Trait.Name, String]) extends Blueprint

case class BlueprintReference(name: String) extends Blueprint with Reference


abstract class AbstractCluster extends Artifact {
  def services: List[AbstractService]
  def sla: Option[Sla]
}

case class Cluster(name: String, services: List[Service], sla: Option[Sla]) extends AbstractCluster


abstract class AbstractService {
  def breed: Breed
  def scale: Option[Scale]
  def routing: Option[Routing]
}

case class Service(breed: Breed, scale: Option[Scale], routing: Option[Routing]) extends AbstractService


trait Sla

case class SlaReference(name: String, escalations: List[Escalation]) extends Reference with Sla

case class AnonymousSla(`type`: String, escalations: List[Escalation], parameters: Map[String, Any]) extends Sla with Anonymous with Type


trait Escalation

case class EscalationReference(name: String) extends Reference with Escalation

case class AnonymousEscalation(`type`: String, parameters: Map[String, Any]) extends Escalation with Anonymous with Type


trait Scale

case class ScaleReference(name: String) extends Reference with Scale

case class AnonymousScale(cpu: Double, memory: Double, instances: Int) extends Scale with Anonymous


trait Routing

case class RoutingReference(name: String) extends Reference with Routing

case class AnonymousRouting(weight: Option[Int], filters: List[Filter]) extends Routing with Anonymous


trait Filter

case class FilterReference(name: String) extends Reference with Filter

case class AnonymousFilter(condition: String) extends Filter with Anonymous
