package io.vamp.model.resolver

import io.vamp.model.artifact._
import org.parboiled.scala._

sealed trait TraitResolverNode

case class StringNode(value: String) extends TraitResolverNode

case class VariableNode(reference: ValueReference) extends TraitResolverNode

class TraitResolverParser extends Parser {

  def parse(expression: String): List[TraitResolverNode] = {

    val nodes = BasicParseRunner(rule(List ~ EOI)).run(expression).result match {
      case Some(list) ⇒ list
      case None       ⇒ throw new RuntimeException(s"can't parse: $expression")
    }

    def compact(nodes: List[TraitResolverNode]): List[TraitResolverNode] = nodes match {
      case StringNode(string1) :: StringNode(string2) :: tail ⇒ compact(StringNode(s"$string1$string2") :: tail)
      case head :: tail ⇒ head :: compact(tail)
      case Nil ⇒ Nil
    }

    compact(nodes)
  }

  def List: Rule1[List[TraitResolverNode]] = rule {
    optional(StringValue) ~ zeroOrMore(SubList1 | SubList2) ~~> ((string, list) ⇒ string.map(_ :: list.flatten).getOrElse(list.flatten))
  }

  def SubList1: Rule1[List[TraitResolverNode]] = rule {
    zeroOrMore(Escaped) ~ VariableReference ~ optional(StringValue) ~~> ((escaped, variable, string) ⇒ escaped ++ (Option(variable) :: string :: Nil).flatten)
  }

  def SubList2: Rule1[List[TraitResolverNode]] = rule {
    oneOrMore(Escaped) ~ optional(StringValue) ~~> ((escaped, string) ⇒ escaped ++ (string :: Nil).flatten)
  }

  def VariableReference = rule {
    ("${" ~ OptionalWhiteSpace ~ Variable ~ OptionalWhiteSpace ~ "}") | ("$" ~ Variable)
  }

  def Variable = rule {
    TraitVariable | HostVariable | LocalVariable
  }

  def LocalVariable = rule {
    VariablePart ~> ((name: String) ⇒ VariableNode(LocalReference(name)))
  }

  def HostVariable = rule {
    VariablePart ~> ((cluster: String) ⇒ VariableNode(HostReference(cluster))) ~ s".${Host.host}"
  }

  def TraitVariable = rule {
    group(VariablePart ~ "." ~ VariablePart ~ "." ~ VariablePart) ~> ((value: String) ⇒ TraitReference.referenceFor(value) match {
      case Some(reference) ⇒ VariableNode(reference)
      case _               ⇒ StringNode(value)
    })
  }

  def VariablePart = rule {
    oneOrMore(noneOf(" \n\r\t\f/[].{}:;,$"))
  }

  def Escaped = rule {
    "$$" ~> (_ ⇒ StringNode("$"))
  }

  def StringValue = rule {
    oneOrMore(noneOf("$")) ~> ((value: String) ⇒ StringNode(value))
  }

  def OptionalWhiteSpace = rule {
    zeroOrMore(anyOf(" \n\r\t\f"))
  }
}
