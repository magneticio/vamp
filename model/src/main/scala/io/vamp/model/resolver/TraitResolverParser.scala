package io.vamp.model.resolver

import akka.parboiled2.{ ParserInput, Rule1 }
import io.vamp.model.artifact._
import io.vamp.model.parser.ParboiledParser

sealed trait TraitResolverNode

case class StringNode(value: String) extends TraitResolverNode

case class VariableNode(reference: ValueReference) extends TraitResolverNode

class TraitResolverParser(val input: ParserInput) extends ParboiledParser[Seq[TraitResolverNode]] {

  def Input: Rule1[Seq[TraitResolverNode]] = rule {
    oneOrMore(Part)
  }

  def Part: Rule1[TraitResolverNode] = rule {
    StringValue | Escaped | Variable | VariableReference
  }

  def VariableReference = rule {
    ("${" ~ OWS ~ Variable ~ OWS ~ "}") | ("$" ~ Variable)
  }

  def Variable = rule {
    TraitVariable | HostVariable | LocalVariable
  }

  def LocalVariable: Rule1[TraitResolverNode] = rule {
    capture(VariablePart) ~> ((name: String) ⇒ VariableNode(LocalReference(name)))
  }

  def HostVariable = rule {
    capture(VariablePart) ~ s".${Host.host}" ~> ((cluster: String) ⇒ VariableNode(HostReference(cluster)))
  }

  def TraitVariable = rule {
    capture(VariablePart ~ "." ~ VariablePart ~ "." ~ VariablePart) ~>
      ((value: String) ⇒ TraitReference.referenceFor(value) match {
        case Some(reference) ⇒ VariableNode(reference)
        case _               ⇒ StringNode(value)
      })
  }

  def VariablePart = rule {
    oneOrMore(noneOf(" \n\r\t\f/[].{}:;,$"))
  }

  def Escaped = rule {
    str("$$") ~> (() ⇒ StringNode("$"))
  }

  def StringValue = rule {
    capture(oneOrMore(noneOf("$"))) ~> StringNode
  }
}
