package io.vamp.model.resolver

import io.vamp.common.notification.NotificationProvider
import io.vamp.common.{ Config, Namespace, NamespaceProvider }
import io.vamp.model.artifact.LocalReference
import io.vamp.model.notification.ParserError

import scala.collection.mutable

trait NamespaceValueResolver extends ValueResolver {
  this: NamespaceProvider with NotificationProvider ⇒

  private lazy val force = Config.boolean("vamp.model.resolvers.force-namespace")()

  def resolveWithNamespace(value: String, lookup: Boolean = false)(implicit namespace: Namespace): String = {
    val variables = Map("namespace" → (if (lookup) namespace.lookupName else namespace.name))
    val result = resolveWithVariables(value, variables)
    if (force && result._2.get("namespace").isEmpty) throwException(ParserError(s"No namespace in: $value"))
    result._1
  }

  def resolveWithVariables(value: String, variables: Map[String, String]): (String, Map[String, String]) = {
    val used = mutable.Map[String, String]()
    val result = nodes(value).map({
      case StringNode(string) ⇒ string
      case VariableNode(LocalReference(name)) if variables.contains(name) ⇒
        val value = variables(name)
        used.put(name, value)
        value
      case _ ⇒ throwException(ParserError(s"Cannot resolve all variables in: $value"))
    }).mkString
    (result, used.toMap)
  }
}
