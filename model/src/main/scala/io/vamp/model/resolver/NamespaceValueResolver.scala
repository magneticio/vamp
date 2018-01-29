package io.vamp.model.resolver

import io.vamp.common.notification.NotificationProvider
import io.vamp.common.{ Config, Namespace, NamespaceProvider }
import io.vamp.model.artifact.{ LocalReference, ValueReference }
import io.vamp.model.notification.ParserError

trait NamespaceValueResolver extends ValueResolver with ClassLoaderValueResolver {
  this: NamespaceProvider with NotificationProvider ⇒

  private val resolversPath = "vamp.model.resolvers.namespace"

  private lazy val force = Config.boolean("vamp.model.resolvers.force-namespace")()

  override def resolverClasses: List[String] = if (Config.has(resolversPath)(namespace)()) Config.stringList(resolversPath)() else Nil

  def resolveWithNamespace(value: String, lookup: Boolean = false)(implicit namespace: Namespace): String = {
    val result = resolveWithOptionalNamespace(value, lookup)
    if (force && result._2.isEmpty) throwException(ParserError(s"No namespace in: $value"))
    result._1
  }

  def resolveWithOptionalNamespace(value: String, lookup: Boolean = false)(implicit namespace: Namespace): (String, Option[String]) = {
    var ns: Option[String] = None
    val cr = super[ClassLoaderValueResolver].valueForReference(Unit)
    val result = nodes(value).map({
      case StringNode(string) ⇒ string
      case VariableNode(LocalReference("namespace")) ⇒
        val value = if (lookup) namespace.lookupName else namespace.name
        ns = Option(value)
        value
      case VariableNode(ref: ValueReference) if cr.isDefinedAt(ref) ⇒
        val value = cr(ref)
        ns = Option(value)
        value
      case _ ⇒ throwException(ParserError(s"Cannot parse the namespace in: $value"))
    }).mkString
    (result, ns)
  }
}
