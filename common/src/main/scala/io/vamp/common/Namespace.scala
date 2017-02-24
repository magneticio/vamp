package io.vamp.common

import java.util.UUID
import scala.language.implicitConversions

object Namespace {

  val default = Namespace("00000000-0000-0000-0000-000000000000")

  def apply(namespace: String): Namespace = Namespace(UUID.fromString(namespace))

  implicit def string2namespace(namespace: String): Namespace = Namespace(namespace)
}

case class Namespace(uuid: UUID) {
  override def toString = uuid.toString
}

trait NamespaceProvider {
  implicit def namespace: Namespace
}
