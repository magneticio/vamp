package io.vamp.model.resolver

import io.vamp.common.notification.{ NotificationErrorException, NotificationProvider }
import io.vamp.model.artifact._
import io.vamp.model.notification.ParserError
import io.vamp.model.parser.Parser

import scala.language.postfixOps

trait ValueResolver {
  this: NotificationProvider ⇒

  val marker = '$'

  def referenceAsPart(reference: ValueReference) = s"$marker{${reference.reference}}"

  def referencesFor(value: String): List[ValueReference] = {
    nodes(value).filter(_.isInstanceOf[VariableNode]).map(_.asInstanceOf[VariableNode].reference)
  }

  def resolve(value: String, provider: (ValueReference ⇒ String)): String = nodes(value).map {
    case StringNode(string)      ⇒ string
    case VariableNode(reference) ⇒ provider(reference)
  } mkString

  private[resolver] def nodes(value: String): List[TraitResolverNode] = {
    try {
      val parser = new Parser[Seq[TraitResolverNode]] {
        override def parser(expression: String) = new TraitResolverParser(expression)
      }
      def compact(nodes: List[TraitResolverNode]): List[TraitResolverNode] = nodes match {
        case StringNode(string1) :: StringNode(string2) :: tail ⇒ compact(StringNode(s"$string1$string2") :: tail)
        case head :: tail ⇒ head :: compact(tail)
        case Nil ⇒ Nil
      }
      compact(parser.parse(value).toList)
    }
    catch {
      case e: NotificationErrorException ⇒ throw e
      case e: Exception                  ⇒ throwException(ParserError(e.getMessage))
    }
  }
}
