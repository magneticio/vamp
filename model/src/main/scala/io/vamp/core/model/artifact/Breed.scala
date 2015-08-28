package io.vamp.core.model.artifact

import java.util.regex.Pattern

import scala.language.implicitConversions

trait Breed extends Artifact

case class DefaultBreed(name: String, deployable: Deployable, ports: List[Port], environmentVariables: List[EnvironmentVariable], constants: List[Constant], dependencies: Map[String, Breed]) extends Breed {
  def traitsFor(group: String): List[Trait] = traitsFor(TraitReference.groupFor(group))

  def traitsFor(group: Option[TraitReference.Value]): List[Trait] = group match {
    case Some(TraitReference.Ports) => ports
    case Some(TraitReference.EnvironmentVariables) => environmentVariables
    case Some(TraitReference.Constants) => constants
    case _ => Nil
  }
}

case class BreedReference(name: String) extends Reference with Breed

case class Deployable(schema: String, definition: Option[String]) extends Artifact {
  def name = Deployable.nameOf(this)
}

object Deployable {

  val schemaDelimiter = "://"

  val defaultSchema = "docker"

  def apply(name: String): Deployable = name.indexOf(schemaDelimiter) match {
    case -1 => Deployable(defaultSchema, Some(name))
    case index => Deployable(name.substring(0, index).trim, Some(name.substring(index + schemaDelimiter.length).trim))
  }

  def nameOf(deployable: Deployable) = deployable match {
    case Deployable(schema, None) => schema
    case Deployable(schema, Some(definition)) => s"$schema$schemaDelimiter$definition"
  }
}

trait Trait {

  def name: String

  def alias: Option[String]

  def value: Option[String]
}

object TraitReference extends Enumeration {

  val Ports, EnvironmentVariables, Constants, Hosts = Value

  val delimiter = "."

  def groupFor(group: String): Option[TraitReference.Value] = group match {
    case "ports" => Some(Ports)
    case "environment_variables" => Some(EnvironmentVariables)
    case "constants" => Some(Constants)
    case "hosts" => Some(Hosts)
    case _ => None
  }

  implicit def groupFor(group: TraitReference.Value): String = group match {
    case Ports => "ports"
    case EnvironmentVariables => "environment_variables"
    case Constants => "constants"
    case Hosts => "hosts"
  }

  def referenceFor(reference: String): Option[TraitReference] = reference.split(Pattern.quote(delimiter), -1) match {
    case Array(cluster, group, value) => Some(TraitReference(cluster, group, value))
    case _ => None
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

object Port extends Enumeration {

  val Tcp, Http = Value

  def portFor(number: Int): Port = Port("", None, Some(number.toString))
}

case class Port(name: String, alias: Option[String], value: Option[String]) extends Trait {
  private val tcp = "/tcp"
  private val http = "/http"

  lazy val number: Int = value match {
    case None => 0
    case Some(v) =>
      if (v.toLowerCase.endsWith(http))
        v.substring(0, v.length - http.length).toInt
      else if (v.toLowerCase.endsWith(tcp))
        v.substring(0, v.length - tcp.length).toInt
      else
        v.toInt
  }

  lazy val `type`: Port.Value = value match {
    case None => Port.Tcp
    case Some(v) => if (v.toLowerCase.endsWith(http)) Port.Http else Port.Tcp
  }
}

case class EnvironmentVariable(name: String, alias: Option[String], value: Option[String], interpolated: Option[String] = None) extends Trait

case class Constant(name: String, alias: Option[String], value: Option[String]) extends Trait
