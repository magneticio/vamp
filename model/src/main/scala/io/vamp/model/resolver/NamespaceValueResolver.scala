package io.vamp.model.resolver

import io.vamp.common.Namespace
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact.LocalReference
import io.vamp.model.notification.ParserError

trait NamespaceValueResolver extends ValueResolver {
  this: NotificationProvider ⇒

  def resolveWithNamespace(value: String)(implicit namespace: Namespace): String = {
    var processed = false
    val result = nodes(value).map({
      case StringNode(string) ⇒ string
      case VariableNode(LocalReference("namespace")) ⇒
        processed = true
        namespace.toString
      case _ ⇒ throwException(ParserError(s"Cannot parse the namespace in: $value"))
    }).mkString
    if (!processed) throwException(ParserError(s"No namespace in: $value"))
    result
  }
}
