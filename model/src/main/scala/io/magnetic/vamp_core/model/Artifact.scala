package io.magnetic.vamp_core.model

import scala.language.implicitConversions

trait Artifact {
  def name: String

  override def toString: String = name
}

trait Reference extends Artifact

trait Type {
  def `type`: String
}

// Breed

trait Breed

case class DefaultBreed(override val name: String, deployable: Deployable, ports: List[Port], environmentVariables: List[EnvironmentVariable], dependencies: Map[String, Breed]) extends Artifact with Breed {
  lazy val traits = ports ++ environmentVariables

  def inTraits: List[Trait] = traits.filter(_.direction == Trait.Direction.In)

  def outTraits: List[Trait] = traits.filter(_.direction == Trait.Direction.Out)

  override def toString: String = s"$name -> $deployable"
}

case class BreedReference(override val name: String) extends Reference with Breed

case class Deployable(override val name: String) extends Artifact

object Trait {

  val host = "host"
  
  object Direction extends Enumeration {
    val In, Out = Value
  }

  case class Name(scope: Option[String], group: Option[String], value: String) {
    override def toString: String = scope match {
      case None => value
      case Some(s) => group match {
        case None => s"$s.$value"
        case Some(g) => s"$s.$g.$value"
      }
    }
  }

  implicit def stringToName(string: String): Name = string.indexOf('.') match {
    case -1 => Name(None, None, string)
    case scopeIndex => string.substring(scopeIndex + 1).indexOf('.') match {
      case -1 => Name(Some(string.substring(0, scopeIndex)), None, string.substring(scopeIndex + 1))
      case groupIndex => Name(Some(string.substring(0, scopeIndex)), Some(string.substring(scopeIndex + 1, scopeIndex + groupIndex + 1)), string.substring(scopeIndex + groupIndex + 2))
    }
  }
}

trait Trait {
  
  def name: Trait.Name

  def alias: Option[String]

  def direction: Trait.Direction.Value

  override def toString: String = name.toString
}

object Port {

  object Type extends Enumeration {
    val Http, Tcp = Value
  }

  case class Value(`type`: Port.Type.Value, number: Int)

  implicit def stringToValue(value: Option[String]): Option[Value] = value flatMap {
    port =>
      val http = s"/${Port.Type.Http.toString.toLowerCase}"
      val tcp = s"/${Port.Type.Tcp.toString.toLowerCase}"

      val `type` = if (port.toLowerCase.endsWith(http)) Port.Type.Http else Port.Type.Tcp
      val number = if (port.toLowerCase.endsWith(http))
        port.substring(0, port.length - http.length).toInt
      else if (port.toLowerCase.endsWith(tcp))
        port.substring(0, port.length - tcp.length).toInt
      else
        port.toInt

      Some(Value(`type`, number))
  }
}

case class Port(override val name: Trait.Name, override val alias: Option[String], value: Option[Port.Value], override val direction: Trait.Direction.Value) extends Trait

case class EnvironmentVariable(override val name: Trait.Name, override val alias: Option[String], value: Option[String], override val direction: Trait.Direction.Value) extends Trait

// Blueprint

case class Blueprint(override val name: String, clusters: List[Cluster], endpoints: Map[String, String], parameters: Map[String, String]) extends Artifact

case class Cluster(override val name: String, services: List[Service], sla: Option[Sla]) extends Artifact

case class Service(breed: Breed, scale: Option[Scale], routing: Option[Routing])


trait Sla

case class SlaReference(override val name: String, escalations: List[Escalation]) extends Reference with Sla

case class AnonymousSla(override val `type`: String, escalations: List[Escalation], parameters: Map[String, Any]) extends Sla with Type


trait Escalation

case class EscalationReference(override val name: String) extends Reference with Escalation

case class AnonymousEscalation(override val `type`: String, parameters: Map[String, Any]) extends Escalation with Type


trait Scale

case class ScaleReference(override val name: String) extends Reference with Scale

case class AnonymousScale(cpu: Double, memory: Double, instances: Int) extends Scale


trait Routing

case class RoutingReference(override val name: String) extends Reference with Routing

case class AnonymousRouting(weight: Option[Int], filters: List[Filter]) extends Routing


trait Filter

case class FilterReference(override val name: String) extends Reference with Filter

case class AnonymousFilter(condition: String) extends Filter

