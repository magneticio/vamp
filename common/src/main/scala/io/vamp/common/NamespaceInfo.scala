package io.vamp.common

/**
 * Provides information of the current namespace
 */
case class NamespaceInfo(metadata: Map[String, Any])

object NamespaceInfo {
  def apply(namespace: Namespace): NamespaceInfo =
    NamespaceInfo(namespace.metadata)

}
