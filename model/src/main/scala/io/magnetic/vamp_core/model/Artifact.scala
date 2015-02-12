package io.magnetic.vamp_core.model

trait Artifact {
  def name: String
}

// Breed

case class Breed(override val name: String, deployable: Deployable, traits: List[Trait], dependencies: Map[String, Dependency]) extends Artifact {
  def ports: List[Trait] = traits.filter(_.`type` == Trait.Type.Port)
  def environmentVariables: List[Trait] = traits.filter(_.`type` == Trait.Type.EnvironmentVariable)
}

case class Dependency(override val name: String) extends Artifact

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

case class Blueprint(override val name: String, clusters: Map[String, Cluster], endpoints: Map[String, String], parameters: Map[String, String]) extends Artifact

case class Cluster(override val name: String, sla: Sla, services: List[Service]) extends Artifact

case class Service(breed: Breed, scale: Scale, routing: Routing, dependencies: Map[String, String])

case class Routing(weight: Int, filters: List[String])

case class Scale(override val name: String, cpu: Double, memory: Double, instances: Int) extends Artifact

case class Sla(override val name: String) extends Artifact


