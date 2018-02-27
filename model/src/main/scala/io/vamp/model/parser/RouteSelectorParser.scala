package io.vamp.model.parser

import akka.parboiled2._

sealed trait RouteSelectorOperand extends Operand

case class IdSelector(value: String) extends RouteSelectorOperand

case class ImageSelector(value: String) extends RouteSelectorOperand

case class LabelSelector(value: String) extends RouteSelectorOperand

class RouteSelectorParser extends Parser[AstNode] {
  override def parser(expression: String) = new RouteSelectorParboiledParser(expression)
}

class RouteSelectorParboiledParser(override val input: ParserInput) extends BooleanParboiledParser(input) {

  override def Operand: Rule1[AstNode] = rule {
    IdOperand | ImageOperand | LabelOperand
  }

  def IdOperand = rule {
    OWS ~ "id" ~ OWS ~ "(" ~ capture(Value) ~ ")" ~> ((value: String) ⇒ IdSelector(value))
  }

  def ImageOperand = rule {
    OWS ~ "image" ~ OWS ~ "(" ~ capture(Value) ~ ")" ~> ((value: String) ⇒ ImageSelector(value))
  }

  def LabelOperand = rule {
    OWS ~ "label" ~ OWS ~ "(" ~ capture(Value) ~ ")" ~> ((value: String) ⇒ LabelSelector(value))
  }

  def Value = rule {
    ValuePart ~ optional('(' ~ ValuePart ~ ')' ~ ValuePart)
  }

  def ValuePart = rule {
    oneOrMore(CharPredicate.Printable -- '(' -- ')')
  }
}
