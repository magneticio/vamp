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
  import org.json4s._

  def toJson(mp: RootAnyMap): JsonAST.JValue = JsonAST.JObject(mp.rootMap.toList.map(kvPair ⇒ JField(kvPair._1, toJson(kvPair._2))))

  private def toJson(v: RestrictedAny) = v match {
    case x: RestrictedInt     ⇒ toJsonInt(x)
    case x: RestrictedString  ⇒ toJsonString(x)
    case x: RestrictedBoolean ⇒ toJsonBoolean(x)
    case x: RestrictedDouble  ⇒ toJsonDouble(x)
    case x: RestrictedList    ⇒ toJsonList(x)
    case x: RestrictedMap     ⇒ toJsonMap(x)
  }
  private def toJsonInt(v: RestrictedInt): JsonAST.JValue = JsonAST.JInt(v.i)
  private def toJsonString(v: RestrictedString): JsonAST.JValue = JsonAST.JString(v.s)
  private def toJsonBoolean(v: RestrictedBoolean): JsonAST.JValue = JsonAST.JBool(v.b)
  private def toJsonDouble(v: RestrictedDouble): JsonAST.JValue = JsonAST.JDouble(v.d)
  private def toJsonList(l: RestrictedList): JsonAST.JValue = JsonAST.JArray(l.ls.map(toJson(_)))
  private def toJsonMap(m: RestrictedMap): JsonAST.JValue = JsonAST.JObject(m.mp.toList.map(kvPair ⇒ JField(kvPair._1, toJson(kvPair._2))))
}

/*
* This type + object will be used where Unit must be returned, Unfortunately, Scala does some type-coercion for Unit so that when a method is declared as returning Unit,
* it can return any type (String, Int, etc). This in particular becomes problematic when wirking with Future[Unit] as you can return Future[String] and more problematically
* Future[Future[Unit]]. Using our own type for this fixes it. When we want a method to retunr Future[Unit], declare the returned type Future[UnitPlaceholder].
* */
sealed trait UnitPlaceholder
case object UnitPlaceholder extends UnitPlaceholder