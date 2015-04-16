package io.vamp.core.model.artifact

import scala.language.implicitConversions

trait Breed extends Artifact

case class DefaultBreed(name: String, deployable: Deployable, ports: List[Port], environmentVariables: List[EnvironmentVariable], constants: List[Constant], dependencies: Map[String, Breed]) extends Breed {
  def traitsFor(group: String): List[Trait] = TraitReference.groupFor(group) match {
    case Some(TraitReference.Ports) => ports
    case Some(TraitReference.EnvironmentVariables) => environmentVariables
    case Some(TraitReference.Constants) => constants
    case _ => Nil
  }
}

case class BreedReference(name: String) extends Reference with Breed

case class Deployable(name: String) extends Artifact


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

  def referenceFor(reference: String): Option[TraitReference] = reference.indexOf(delimiter) match {
    case -1 => None
    case clusterIndex => reference.substring(clusterIndex + 1).indexOf(delimiter) match {
      case -1 => None
      case groupIndex =>
        val cluster = reference.substring(0, clusterIndex)
        val group = reference.substring(clusterIndex + 1, clusterIndex + groupIndex + 1)
        val value = reference.substring(clusterIndex + groupIndex + 2)
        Some(TraitReference(cluster, group, value))
    }
  }
}

trait ValueReference {
  def cluster: String

  def reference: String

  override def toString = reference
}

case class TraitReference(cluster: String, group: String, name: String) extends ValueReference {
  lazy val reference = s"$cluster.$group.$name"
}


object Port extends Enumeration {

  val Tcp, Http = Value
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

case class EnvironmentVariable(name: String, alias: Option[String], value: Option[String]) extends Trait

case class Constant(name: String, alias: Option[String], value: Option[String]) extends Trait
