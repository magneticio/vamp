package io.vamp.model.resolver

import io.vamp.common.Namespace
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact.LocalReference
import io.vamp.model.notification.ParserError

trait NamespaceValueResolver extends ValueResolver {
  this: NotificationProvider ⇒

  def resolveWithNamespace(value: String, lookup: Boolean = false)(implicit namespace: Namespace): String = {
    var processed = false
    val result = nodes(value).map({
      case StringNode(string) ⇒ string
      case VariableNode(LocalReference("namespace")) ⇒
        processed = true
        if (lookup) namespace.lookupName else namespace.name
      case _ ⇒ throwException(ParserError(s"Cannot parse the namespace in: $value"))
    }).mkString
    if (!processed) throwException(ParserError(s"No namespace in: $value"))
    result
  }
}
