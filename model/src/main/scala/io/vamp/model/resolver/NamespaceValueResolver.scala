package io.vamp.model.resolver

import io.vamp.common.notification.NotificationProvider
import io.vamp.common.{ Config, Namespace, NamespaceProvider }
import io.vamp.model.artifact.LocalReference
import io.vamp.model.notification.ParserError

trait NamespaceValueResolver extends ValueResolver {
  this: NamespaceProvider with NotificationProvider ⇒

  private lazy val force = Config.boolean("vamp.model.resolvers.force-namespace")()

  def resolveWithNamespace(value: String, lookup: Boolean = false)(implicit namespace: Namespace): String = {
    val result = resolveWithOptionalNamespace(value, lookup)
    if (force && result._2.isEmpty) throwException(ParserError(s"No namespace in: $value"))
    result._1
  }

  def resolveWithOptionalNamespace(value: String, lookup: Boolean = false)(implicit namespace: Namespace): (String, Option[String]) = {
    var ns: Option[String] = None
    val result = nodes(value).map({
      case StringNode(string) ⇒ string
      case VariableNode(LocalReference("namespace")) ⇒
        val value = if (lookup) namespace.lookupName else namespace.name
        ns = Option(value)
        value
      case _ ⇒ throwException(ParserError(s"Cannot parse the namespace in: $value"))
    }).mkString
    (result, ns)
  }
}
