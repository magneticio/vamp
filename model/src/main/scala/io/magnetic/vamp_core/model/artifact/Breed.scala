package io.magnetic.vamp_core.model.artifact

import scala.language.implicitConversions

trait Breed extends Artifact

case class DefaultBreed(name: String, deployable: Deployable, ports: List[Port], environmentVariables: List[EnvironmentVariable], dependencies: Map[String, Breed]) extends Breed {
  lazy val traits = ports ++ environmentVariables

  def inTraits: List[Trait[_]] = traits.filter(_.direction == Trait.Direction.In)

  def outTraits: List[Trait[_]] = traits.filter(_.direction == Trait.Direction.Out)
}

case class BreedReference(name: String) extends Reference with Breed

case class Deployable(name: String) extends Artifact

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

trait Trait[A] {

  def name: Trait.Name

  def alias: Option[String]

  def direction: Trait.Direction.Value

  def value: Option[A]
}

object Port {
  def toPort(name: Trait.Name, alias: Option[String], value: Option[String], direction: Trait.Direction.Value): Port = value match {
    case None => TcpPort(name, alias, None, direction)
    case Some(portValue) =>
      val tcp = "tcp"
      val http = "http"

      val number = if (portValue.toLowerCase.endsWith(http))
        portValue.substring(0, portValue.length - http.length - 1).toInt
      else if (portValue.toLowerCase.endsWith(tcp))
        portValue.substring(0, portValue.length - tcp.length - 1).toInt
      else
        portValue.toInt

      if (portValue.toLowerCase.endsWith(http))
        HttpPort(name, alias, Some(number), direction)
      else
        TcpPort(name, alias, Some(number), direction)
  }
}

trait Port extends Trait[Int]

case class TcpPort(name: Trait.Name, alias: Option[String], value: Option[Int], direction: Trait.Direction.Value) extends Port

case class HttpPort(name: Trait.Name, alias: Option[String], value: Option[Int], direction: Trait.Direction.Value) extends Port


case class EnvironmentVariable(name: Trait.Name, alias: Option[String], value: Option[String], direction: Trait.Direction.Value) extends Trait[String]
