package io.magnetic.vamp_core.model

import scala.language.implicitConversions

trait Breed extends Artifact

case class DefaultBreed(override val name: String, deployable: Deployable, ports: List[Port], environmentVariables: List[EnvironmentVariable], dependencies: Map[String, Breed]) extends Artifact with Breed {
  lazy val traits = ports ++ environmentVariables

  def inTraits: List[Trait] = traits.filter(_.direction == Trait.Direction.In)

  def outTraits: List[Trait] = traits.filter(_.direction == Trait.Direction.Out)
}

case class BreedReference(override val name: String) extends Reference with Breed

case class Deployable(override val name: String) extends Artifact

object Trait {

  val host = "host"

  object Direction extends Enumeration {
    val In, Out = Value
  }

  object Name {

    object Group extends Enumeration {
      val Ports, EnvironmentVariables = Value
    }

    val delimiter = "."

    implicit def asName(string: String): Name = string.indexOf(delimiter) match {
      case -1 => Name(None, None, string)
      case scopeIndex => string.substring(scopeIndex + 1).indexOf(delimiter) match {
        case -1 => Name(Some(string.substring(0, scopeIndex)), None, string.substring(scopeIndex + 1))
        case groupIndex => 
          val scope = Some(string.substring(0, scopeIndex))
          val group = string.substring(scopeIndex + 1, scopeIndex + groupIndex + 1) match {
            case g if g == "ports" => Some(Name.Group.Ports)
            case g if g == "environment_variables" => Some(Name.Group.EnvironmentVariables)
            case _ => None
          }
          val value = string.substring(scopeIndex + groupIndex + 2)
          
          Name(scope, group, value)
      }
    }
  }

  case class Name(scope: Option[String], group: Option[Name.Group.Value], value: String) {
    override def toString: String = scope match {
      case None => value
      case Some(s) => group match {
        case None => s"$s${Name.delimiter}$value"
        case Some(Name.Group.EnvironmentVariables) => s"$s${Name.delimiter}environment_variables${Name.delimiter}$value"
        case Some(g) => s"$s${Name.delimiter}${g.toString.toLowerCase}${Name.delimiter}$value"
      }
    }
  }

}

trait Trait {

  def name: Trait.Name

  def alias: Option[String]

  def direction: Trait.Direction.Value
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
