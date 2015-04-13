package io.vamp.core.model.artifact

import scala.language.implicitConversions

trait Breed extends Artifact

case class DefaultBreed(name: String, deployable: Deployable, ports: List[Port], environmentVariables: List[EnvironmentVariable], constants: List[Constant], dependencies: Map[String, Breed]) extends Breed {
  def traitsFor(group: String): List[Trait] = group match {
    case "ports" => ports
    case "environment_variables" => environmentVariables
    case "constants" => constants
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

object TraitReference {

  val delimiter = "."

  def referenceFor(name: String): Option[TraitReference] = name.indexOf(delimiter) match {
    case -1 => None
    case clusterIndex => name.substring(clusterIndex + 1).indexOf(delimiter) match {
      case -1 => None
      case groupIndex =>
        val cluster = name.substring(0, clusterIndex)
        val group = name.substring(clusterIndex + 1, clusterIndex + groupIndex + 1)
        val value = name.substring(clusterIndex + groupIndex + 2)
        Some(TraitReference(cluster, group, value))
    }
  }

}

case class TraitReference(cluster: String, group: String, name: String)


case class Port(name: String, alias: Option[String], value: Option[String]) extends Trait

case class EnvironmentVariable(name: String, alias: Option[String], value: Option[String]) extends Trait

case class Constant(name: String, alias: Option[String], value: Option[String]) extends Trait
