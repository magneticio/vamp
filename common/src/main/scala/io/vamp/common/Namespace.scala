package io.vamp.common

import scala.language.implicitConversions

object Namespace {

  implicit def string2namespace(namespace: String): Namespace = Namespace(namespace)

  def apply(name: String, config: Map[String, Any] = Map()): Namespace = new Namespace(name, name, config)
}

case class Namespace(name: String, id: String, config: Map[String, Any]) extends Artifact with Reference {
  val kind = "namespace"
}

trait NamespaceProvider {
  implicit def namespace: Namespace
}
