package io.vamp.model.resolver

import io.vamp.common.{ Namespace, NamespaceProvider }
import io.vamp.model.artifact.ValueReference

abstract class ClassValueResolver(namespace: Namespace) {
  def valueForReference(context: AnyRef): PartialFunction[ValueReference, String]
}

trait ClassLoaderValueResolver {
  this: NamespaceProvider ⇒

  private lazy val resolvers = resolverClasses.map { clazz ⇒
    Class.forName(clazz).getConstructor(classOf[Namespace]).newInstance(namespace).asInstanceOf[ClassValueResolver]
  }

  def resolverClasses: List[String] = Nil

  def valueForReference(context: AnyRef): PartialFunction[ValueReference, String] = {
    if (resolvers.isEmpty) PartialFunction.empty else resolvers.map(_.valueForReference(context)).reduce(_ orElse _)
  }
}
