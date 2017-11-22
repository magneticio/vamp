package io.vamp.model.artifact

import io.vamp.common.{ Artifact, RootAnyMap }

import scala.language.implicitConversions

case class Instance(name: String, host: String, ports: Map[String, Int], deployed: Boolean) extends Artifact {
  val kind: String = "instances"

  val metadata = RootAnyMap.empty
}

object Host {
  val host = "host"
}

case class Host(name: String, value: Option[String]) extends Trait {
  def alias: Option[String] = None
}

object HostReference {

  val delimiter: String = TraitReference.delimiter

  def referenceFor(reference: String): Option[HostReference] = reference.indexOf(delimiter) match {
    case -1 ⇒ None
    case clusterIndex ⇒
      val cluster = reference.substring(0, clusterIndex)
      val name = reference.substring(clusterIndex + 1)
      if (name == Host.host) Some(HostReference(cluster)) else None
  }
}

case class HostReference(cluster: String) extends ClusterReference {
  def asTraitReference: String = TraitReference(cluster, TraitReference.Hosts, Host.host).toString

  lazy val reference = s"$cluster${HostReference.delimiter}${Host.host}"
}

object NoGroupReference {

  val delimiter: String = TraitReference.delimiter

  def referenceFor(reference: String): Option[NoGroupReference] = reference.indexOf(delimiter) match {
    case -1 ⇒ None
    case clusterIndex ⇒
      val cluster = reference.substring(0, clusterIndex)
      val name = reference.substring(clusterIndex + 1)
      Some(NoGroupReference(cluster, name))
  }
}

case class NoGroupReference(cluster: String, name: String) extends ClusterReference {
  def asTraitReference(group: String): String = TraitReference(cluster, group, name).toString

  lazy val reference = s"$cluster${NoGroupReference.delimiter}$name"
}