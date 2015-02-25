package io.magnetic.vamp_core.model.artifact

case class Blueprint(override val name: String, clusters: List[Cluster], endpoints: Map[Trait.Name, String], parameters: Map[Trait.Name, String]) extends Artifact

case class Cluster(override val name: String, services: List[Service], sla: Option[Sla]) extends Artifact

case class Service(breed: Breed, scale: Option[Scale], routing: Option[Routing])


trait Sla

case class SlaReference(override val name: String, escalations: List[Escalation]) extends Reference with Sla

case class AnonymousSla(override val `type`: String, escalations: List[Escalation], parameters: Map[String, Any]) extends Sla with Anonymous with Type


trait Escalation

case class EscalationReference(override val name: String) extends Reference with Escalation

case class AnonymousEscalation(override val `type`: String, parameters: Map[String, Any]) extends Escalation with Anonymous with Type


trait Scale

case class ScaleReference(override val name: String) extends Reference with Scale

case class AnonymousScale(cpu: Double, memory: Double, instances: Int) extends Scale with Anonymous


trait Routing

case class RoutingReference(override val name: String) extends Reference with Routing

case class AnonymousRouting(weight: Option[Int], filters: List[Filter]) extends Routing with Anonymous


trait Filter

case class FilterReference(override val name: String) extends Reference with Filter

case class AnonymousFilter(condition: String) extends Filter with Anonymous
