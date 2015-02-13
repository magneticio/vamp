package io.magnetic.vamp_core.model

trait Artifact {
  def name: String
}

trait Reference extends Artifact

trait Type {
  def `type`: String
}

// Breed

trait Breed

case class DefaultBreed(override val name: String, deployable: Deployable, traits: List[Trait], dependencies: Map[String, Breed]) extends Artifact with Breed {
  def ports: List[Trait] = traits.filter(_.`type` == Trait.Type.Port)
  def environmentVariables: List[Trait] = traits.filter(_.`type` == Trait.Type.EnvironmentVariable)
}

case class BreedReference(override val name: String) extends Reference with Breed

case class Deployable(override val name: String) extends Artifact

case class Trait(override val name: String, alias: Option[String], value: Option[String], `type`: Trait.Type.Value, direction: Trait.Direction.Value) extends Artifact {

  def portType: Option[Trait.Port.Value] = `type` match {
    case Trait.Type.Port => value match {
      case None => Some(Trait.Port.Tcp)
      case Some(v) => if (v.toLowerCase.endsWith("/http")) Some(Trait.Port.Http) else Some(Trait.Port.Tcp)
    }
    case _ => None
  }
}

object Trait {

  object Port extends Enumeration {
    val Http, Tcp = Value
  }

  object Type extends Enumeration {
    val Port, EnvironmentVariable, Volume = Value
  }

  object Direction extends Enumeration {
    val In, Out = Value
  }
}

// Blueprint

case class Blueprint(override val name: String, clusters: List[Cluster], endpoints: Map[String, String], parameters: Map[String, String]) extends Artifact

case class Cluster(override val name: String, services: List[Service], sla: Option[Sla]) extends Artifact

case class Service(breed: Breed, scale: Option[Scale], routing: Option[Routing], dependencies: Map[String, String])

case class Routing(weight: Option[Int], filters: List[Filter])


trait Sla

case class SlaReference(override val name: String) extends Reference with Sla

case class AnonymousSla(override val `type`: String, escalations: List[Escalation], parameters: Map[String, Any]) extends Sla with Type


trait Escalation

case class EscalationReference(override val name: String) extends Reference with Escalation

case class AnonymousEscalation(override val `type`: String, parameters: Map[String, Any]) extends Escalation with Type


trait Scale

case class ScaleReference(override val name: String) extends Reference with Scale

case class AnonymousScale(cpu: Double, memory: Double, instances: Int) extends Scale


trait Filter

case class FilterReference(override val name: String) extends Reference with Filter

case class AnonymousFilter(condition: String) extends Filter

