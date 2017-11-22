package io.vamp.common

import io.vamp.common.util.HashUtil
import java.util.UUID

object Id {
  def apply[A](uuid: UUID) = new Id[A](uuid.toString)
  def generate[A] = new Id[A](UUID.randomUUID.toString)
}

case class Id[+A](value: String) extends Serializable {
  override def toString = value
  def originalUuid = UUID.fromString(value)
  def uuid = UUID.nameUUIDFromBytes(value.getBytes("UTF-8"))

  def convert[T]: Id[T] = Id[T](value)
}

object Artifact {

  val version = "v1"

  val kind: String = "kind"

  val metadata = "metadata"
}

trait Artifact {
  
  def name: String

  def kind: String

  def metadata: RootAnyMap
}

trait Reference extends Artifact {
  val metadata = RootAnyMap.empty
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

sealed trait RestrictedAny
case class RestrictedInt(i: Int) extends RestrictedAny
case class RestrictedDouble(d: Double) extends RestrictedAny
case class RestrictedString(s: String) extends RestrictedAny
case class RestrictedBoolean(b: Boolean) extends RestrictedAny
case class RestrictedList(ls: List[RestrictedAny]) extends RestrictedAny
case class RestrictedMap(mp: Map[String, RestrictedAny]) extends RestrictedAny
case class RootAnyMap(rootMap: Map[String, RestrictedAny])
object RootAnyMap {
  def empty: RootAnyMap = RootAnyMap(Map())
}