package io.vamp.common

import scala.language.implicitConversions

case class Namespace(
    name: String,
    config: Map[String, Any] = Map(),
    metadata: Map[String, Any] = Map()) extends Artifact {
  val kind = "namespace"
  val parent: String = name.take(name.lastIndexOf("-"))
}

trait NamespaceProvider {
  implicit def namespace: Namespace
}
