package io.vamp.model.artifact

import java.util.regex.Pattern

import io.vamp.common.notification.NotificationErrorException
import io.vamp.model.notification.{ InvalidArgumentError, InvalidArgumentValueError }

import scala.language.implicitConversions
import scala.util.Try

trait Breed extends Artifact {
  val kind = "breed"
}

case class DefaultBreed(
    name: String,
    deployable: Deployable,
    ports: List[Port],
    environmentVariables: List[EnvironmentVariable],
    constants: List[Constant],
    arguments: List[Argument],
    dependencies: Map[String, Breed]) extends Breed {

  def traitsFor(group: String): List[Trait] = traitsFor(TraitReference.groupFor(group))

  def traitsFor(group: Option[TraitReference.Value]): List[Trait] = group match {
    case Some(TraitReference.Ports)                ⇒ ports
    case Some(TraitReference.EnvironmentVariables) ⇒ environmentVariables
    case Some(TraitReference.Constants)            ⇒ constants
    case _                                         ⇒ Nil
  }

  lazy val traits: List[Trait] = ports ++ environmentVariables ++ constants
}

case class BreedReference(name: String) extends Reference with Breed

object Deployable {

  def apply(definition: String): Deployable = Deployable("container/docker", definition)
}

case class Deployable(`type`: String, definition: String)

trait Trait {

  def name: String

  def alias: Option[String]

  def value: Option[String]
}

object TraitReference extends Enumeration {

  val Ports, EnvironmentVariables, Constants, Hosts = Value

  val delimiter = "."

  def groupFor(group: String): Option[TraitReference.Value] = group match {
    case "ports"                 ⇒ Some(Ports)
    case "environment_variables" ⇒ Some(EnvironmentVariables)
    case "constants"             ⇒ Some(Constants)
    case "hosts"                 ⇒ Some(Hosts)
    case _                       ⇒ None
  }

  implicit def groupFor(group: TraitReference.Value): String = group match {
    case Ports                ⇒ "ports"
    case EnvironmentVariables ⇒ "environment_variables"
    case Constants            ⇒ "constants"
    case Hosts                ⇒ "hosts"
  }

  def referenceFor(reference: String): Option[TraitReference] = reference.split(Pattern.quote(delimiter), -1) match {
    case Array(cluster, group, value) ⇒ Some(TraitReference(cluster, group, value))
    case _                            ⇒ None
  }
}

trait ValueReference {
  def cluster: String

  def reference: String

  override def toString = reference
}

case class LocalReference(name: String) extends ValueReference {
  val cluster = ""

  lazy val reference = name
}

case class TraitReference(cluster: String, group: String, name: String) extends ValueReference {
  lazy val reference = s"$cluster.$group.$name"

  def referenceWithoutGroup = s"$cluster.$name"
}

object Port {

  private val tcp = Port.Type.toTypeString(Port.Type.Tcp)
  private val http = Port.Type.toTypeString(Port.Type.Http)

  object Type extends Enumeration {
    val Tcp, Http = Value

    def toTypeString(value: Port.Type.Value) = s"/${value.toString.toLowerCase}"
  }

  def apply(number: Int): Port = Port(number.toString, None, Some(number.toString))

  def apply(value: String): Port = Port("", None, Some(value)) match {
    case port ⇒ port.copy(name = port.number.toString)
  }

  def apply(number: Int, `type`: Port.Type.Value): Port = Port(number.toString, None, Option(s"$number${Port.Type.toTypeString(`type`)}"))

  def apply(name: String, alias: Option[String], value: Option[String]): Port = {

    val number: Int = value match {
      case None ⇒ 0
      case Some(v) ⇒
        if (v.toLowerCase.endsWith(http))
          v.substring(0, v.length - http.length).toInt
        else if (v.toLowerCase.endsWith(tcp))
          v.substring(0, v.length - tcp.length).toInt
        else
          Try(v.toInt).getOrElse(0)
    }

    val `type`: Port.Type.Value = value match {
      case None    ⇒ Port.Type.Http
      case Some(v) ⇒ if (v.toLowerCase.endsWith(tcp)) Port.Type.Tcp else Port.Type.Http
    }

    Port(name, alias, value, number, `type`)
  }
}

case class Port(name: String, alias: Option[String], value: Option[String], number: Int, `type`: Port.Type.Value) extends Trait {
  val assigned = number > 0

  def toValue = s"$number${Port.Type.toTypeString(`type`)}"
}

case class EnvironmentVariable(name: String, alias: Option[String], value: Option[String], interpolated: Option[String] = None) extends Trait

case class Constant(name: String, alias: Option[String], value: Option[String]) extends Trait

object Argument {

  val privileged = "privileged"

  def apply(argument: String): Argument = {

    val result = argument.split("=", 2).toList match {
      case key :: value :: Nil ⇒ Argument(key.trim, value.trim)
      case any                 ⇒ throw NotificationErrorException(InvalidArgumentError, if (any != null) any.toString else "")
    }

    if (result.privileged && Try(result.value.toBoolean).isFailure) throw NotificationErrorException(InvalidArgumentValueError(result), s"${result.key} -> ${result.value}")

    result
  }
}

case class Argument(key: String, value: String) {
  val privileged = key == Argument.privileged
}
