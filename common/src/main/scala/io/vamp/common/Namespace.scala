package io.vamp.common

object Namespace {
  val kind: String = "namespaces"
}

case class Namespace(
    name:     String,
    config:   Map[String, Any] = Map(),
    metadata: RootAnyMap       = RootAnyMap.empty
) extends Artifact with Lookup {
  val kind: String = Namespace.kind
}

trait NamespaceProvider {
  implicit def namespace: Namespace
}
