package io.vamp.model.resolver

import io.vamp.model.artifact._

import scala.language.postfixOps

trait TraitResolver {

  val marker = '$'

  def referenceAsPart(reference: ValueReference) = s"$marker{${reference.reference}}"

  def referencesFor(value: String): List[ValueReference] = {
    nodes(value).filter(_.isInstanceOf[VariableNode]).map(_.asInstanceOf[VariableNode].reference)
  }

  def resolve(value: String, provider: (ValueReference ⇒ String)): String = nodes(value).map {
    case StringNode(string)      ⇒ string
    case VariableNode(reference) ⇒ provider(reference)
  } mkString

  private[resolver] def nodes(value: String): List[TraitResolverNode] = new TraitResolverParser().parse(value)
}
