package io.vamp.common

trait NamespaceResolver {
  def namespace: String
}

trait NamespaceResolverProvider {
  implicit def namespaceResolver: NamespaceResolver
}
