package io.vamp.common

case class Namespace(
    name:     String,
    config:   Map[String, Any] = Map(),
    metadata: Map[String, Any] = Map()) extends Artifact {
  val kind = "namespace"
}

trait NamespaceProvider {
  implicit def namespace: Namespace
}
