package io.vamp.common

object Namespace {
  val kind = "namespaces"
}

case class Namespace(
    name:     String,
    config:   Map[String, Any] = Map(),
    metadata: Map[String, Any] = Map()) extends Artifact with Lookup {
  val kind = Namespace.kind
}

trait NamespaceProvider {
  implicit def namespace: Namespace
}
