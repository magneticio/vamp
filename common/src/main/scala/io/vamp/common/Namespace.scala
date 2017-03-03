package io.vamp.common

import scala.language.implicitConversions

object Namespace {
  implicit def string2namespace(namespace: String): Namespace = Namespace(namespace)
}

case class Namespace(name: String, config: Map[String, Any] = Map(), metadata: Map[String, Any] = Map()) extends Artifact {
  val kind = "namespace"
}

trait NamespaceProvider {
  implicit def namespace: Namespace
}
