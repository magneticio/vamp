package io.vamp.common

import scala.language.implicitConversions

object Namespace {
  implicit def string2namespace(namespace: String): Namespace = Namespace(namespace)
}

case class Namespace(name: String) {
  val id = name
  override def toString = name
}

trait NamespaceProvider {
  implicit def namespace: Namespace
}
