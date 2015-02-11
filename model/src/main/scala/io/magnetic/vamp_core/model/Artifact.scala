package io.magnetic.vamp_core.model

trait Artifact {
  def name: String
}

// Breed

case class Breed(override val name: String, deployable: Deployable, traits: List[Trait], dependencies: List[BreedDependency]) extends Artifact

case class BreedDependency(override val name: String) extends Artifact

case class Deployable(override val name: String) extends Artifact

case class Trait(override val name: String, alias: String, value: String, `type`: Trait.Type.Value, direction: Trait.Direction.Value) extends Artifact {

  def portType: Option[Trait.Port.Value] = `type` match {
    case Trait.Type.Port => if (value.toLowerCase.endsWith("/http")) Some(Trait.Port.HTTP) else Some(Trait.Port.TCP)
    case _ => None
  }
}

object Trait {

  object Port extends Enumeration {
    val HTTP, TCP = Value
  }

  object Type extends Enumeration {
    val Port, EnvironmentVariable, Volume = Value
  }

  object Direction extends Enumeration {
    val IN, OUT = Value
  }

}

// Blueprint

case class Blueprint(override val name: String, clusters: Map[String, Cluster], endpoints: Map[String, String], parameters: Map[String, String]) extends Artifact

case class Cluster(override val name: String, sla: Sla, services: List[Service]) extends Artifact

case class Service(breed: Breed, scale: Scale, routing: Routing, dependencies: Map[String, String])

case class Routing(weight: Int, filters: List[String])

case class Scale(override val name: String, cpu: Double, memory: Double, instances: Int) extends Artifact

case class Sla(override val name: String) extends Artifact


