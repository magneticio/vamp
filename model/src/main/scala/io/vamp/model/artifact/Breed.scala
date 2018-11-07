package io.vamp.model.artifact

import java.util.regex.Pattern

import com.typesafe.scalalogging.LazyLogging
import io.vamp.common._
import io.vamp.common.notification.NotificationErrorException
import io.vamp.model.notification.{ InvalidArgumentError, InvalidArgumentValueError }
import io.vamp.model.reader.Time

import scala.language.implicitConversions
import scala.util.Try

object Breed {
  val kind: String = "breeds"
}

trait Breed extends Artifact with Lookup {
  val kind: String = Breed.kind
}

case class DefaultBreed(
    name:                 String,
    metadata:             Map[String, Any],
    deployable:           Deployable,
    ports:                List[Port],
    environmentVariables: List[EnvironmentVariable],
    constants:            List[Constant],
    arguments:            List[Argument],
    dependencies:         Map[String, Breed],
    healthChecks:         Option[List[HealthCheck]]
) extends Breed {

  def traitsFor(group: String): List[Trait] = traitsFor(TraitReference.groupFor(group))

  def traitsFor(group: Option[TraitReference.Value]): List[Trait] = group match {
    case Some(TraitReference.Ports)                ⇒ ports
    case Some(TraitReference.EnvironmentVariables) ⇒ environmentVariables
    case Some(TraitReference.Constants)            ⇒ constants
    case _                                         ⇒ Nil
  }

  lazy val traits: List[Trait] = ports ++ environmentVariables ++ constants

  def traitsExceptEnvironmentVariables(): List[Trait] = ports ++ constants
}

case class BreedReference(name: String) extends Reference with Breed

object Deployable {

  val defaultType = "container/docker"

  def apply(definition: String): Deployable = Deployable(None, definition)

  def apply(`type`: String, definition: String): Deployable = Deployable(Option(`type`), definition)
}

case class Deployable(`type`: Option[String], definition: String) extends LazyLogging {
  def defaultType()(implicit namespace: Namespace): String = `type`.getOrElse {
    val path = "vamp.model.default-deployable-type"
    val deployableType = {
      if (Config.has(path)(namespace)()) Config.string(path)() else Deployable.defaultType
    }
    logger.info("Deployable type: {}", deployableType)
    deployableType
  }
}

trait Trait {

  def name: String

  def alias: Option[String]

  def value: Option[String]
}

object TraitReference extends Enumeration with LazyLogging {

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

  // Limit is set to 3 to allow values to have dots in it
  def referenceFor(reference: String): Option[TraitReference] = reference.split(Pattern.quote(delimiter), 3) match {
    case Array(cluster, group, value) ⇒ Some(TraitReference(cluster, group, value))
    case _ ⇒
      logger.warn("TraitReference - Reference was in an unexpected format {}", reference)
      None
  }
}

trait ValueReference {

  def reference: String

  override def toString: String = reference
}

trait ClusterReference extends ValueReference {
  def cluster: String
}

case class LocalReference(name: String) extends ClusterReference {
  val cluster = ""

  lazy val reference: String = name
}

case class TraitReference(cluster: String, group: String, name: String) extends ClusterReference {
  lazy val reference = s"$cluster.$group.$name"

  def referenceWithoutGroup = s"$cluster.$name"
}

object GlobalReference {
  val schemaDelimiter = "://"

  def apply(reference: String): GlobalReference = reference.split(Pattern.quote(schemaDelimiter), 2).toList match {
    case schema :: path :: Nil ⇒ GlobalReference(schema, path)
    case any                   ⇒ throw NotificationErrorException(InvalidArgumentError, if (any != null) any.toString else "")
  }
}

case class GlobalReference(schema: String, path: String) extends ValueReference {
  lazy val reference = s"$schema${GlobalReference.schemaDelimiter}$path"
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
  val assigned: Boolean = number > 0

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
  val privileged: Boolean = key == Argument.privileged
}

/**
 * Vamp definition of a HealthCheck
 * Transforms later into specific 'container solution'
 */
case class HealthCheck(
  path:         String,
  port:         String,
  initialDelay: Time,
  timeout:      Time,
  interval:     Time,
  failures:     Int,
  protocol:     String
)
