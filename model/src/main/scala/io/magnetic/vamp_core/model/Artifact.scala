package io.magnetic.vamp_core.model

trait Artifact {
  def name: String
}

// Breed

case class Breed(override val name: String, deployable: Deployable, traits: List[Trait], dependencies: Map[String, Breed]) extends Artifact

case class Deployable(override val name: String) extends Artifact

case class Trait(override val name: String, alias: String, value: String, `type`: Type.Value, direction: Direction.Value) extends Artifact {

  object Port extends Enumeration {
    val HTTP, TCP = Value
  }

  def portType: Option[Port.Value] = `type` match {
    case Type.Port => if (value.toLowerCase.endsWith("/http")) Some(Port.HTTP) else Some(Port.TCP)
    case _ => None
  }
}

object Type extends Enumeration {
  val Port, EnvironmentVariable, Volume = Value
}

object Direction extends Enumeration {
  val IN, OUT = Value
}

// Blueprint

case class Blueprint(override val name: String, clusters: Map[String, Cluster], endpoints: Map[String, String], parameters: Map[String, String]) extends Artifact

case class Cluster(override val name: String, sla: Sla, services: List[Service]) extends Artifact

case class Service(breed: Breed, scale: Scale, routing: Routing, dependencies: Map[String, String])

case class Routing(weight: Int, filters: List[String])

case class Scale(override val name: String, cpu: Double, memory: Double, instances: Int) extends Artifact

case class Sla(override val name: String) extends Artifact


