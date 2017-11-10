package io.vamp.common

import io.vamp.common.util.HashUtil
import java.util.UUID

object Id {
  def apply[A](uuid:UUID) = new Id[A](uuid.toString)
  def generate[A] = new Id[A](UUID.randomUUID.toString)
}

case class Id[+A](value : String) extends Serializable {
  override def toString = value
  def originalUuid = UUID.fromString(value)
  def uuid = UUID.nameUUIDFromBytes(value.getBytes("UTF-8"))

  def convert[T] : Id[T] = Id[T](value)
}


object Artifact {

  val version = "v1"

  val kind: String = "kind"

  val metadata = "metadata"
}

trait Artifact {

  def id: Id[Artifact] = Id[Artifact](name)

  def name: String

  def kind: String

  def metadata: Map[String, Any]
}

trait Reference extends Artifact {
  val metadata = Map[String, Any]()
}

trait Type {
  def `type`: String
}

object Lookup {
  val entry = "lookup_name"
}

trait Lookup extends Artifact {

  def lookupName = lookup(name)

  def lookup(string: String) = HashUtil.hexSha1(s"$getClass@$string", Artifact.version)
}
