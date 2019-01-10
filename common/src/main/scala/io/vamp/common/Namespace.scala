package io.vamp.common

import scala.util.Try

object Namespace {
  val kind: String = "namespaces"
}

case class Namespace(
    name:     String,
    config:   Map[String, Any] = Map(),
    metadata: Map[String, Any] = Map()
) extends Artifact with Lookup {
  val kind: String = Namespace.kind
}

trait NamespaceProvider {
  implicit def namespace: Namespace
  /*
  custom Namespace is added to provide a custom Namespace for kubernetes environments.
  It should be usable wherever NamespaceProvider defined. Currently it is only used in kubernetes driver.
  This val should be lazy, otherwise it fails since vals are initialized before defs are evaluated
   */
  lazy val customNamespace: String = {
    // Support for custom namespaces for kubernetes is added See: https://magneticio.atlassian.net/browse/VE-456
    Try(Config.string("vamp.container-driver.kubernetes.namespace").apply())
      .getOrElse(namespace.name)
  }
}
