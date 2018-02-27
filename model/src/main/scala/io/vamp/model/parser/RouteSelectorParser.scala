package io.vamp.model.parser

import akka.parboiled2._

sealed trait RouteSelectorOperand extends Operand

case class NameSelector(value: String) extends RouteSelectorOperand

case class KindSelector(value: String) extends RouteSelectorOperand

case class NamespaceSelector(value: String) extends RouteSelectorOperand

case class ImageSelector(value: String) extends RouteSelectorOperand

case class LabelSelector(value: String) extends RouteSelectorOperand

case class IpSelector(value: String) extends RouteSelectorOperand

case class PortSelector(value: Int) extends RouteSelectorOperand

class RouteSelectorParser extends Parser[AstNode] {
  override def parser(expression: String) = new RouteSelectorParboiledParser(expression)
}

class RouteSelectorParboiledParser(override val input: ParserInput) extends BooleanParboiledParser(input) {

  override def Operand: Rule1[AstNode] = rule {
    NamespaceOperand | NameOperand | KindOperand | ImageOperand | LabelOperand | IpOperand | PortOperand
  }

  def NameOperand = rule {
    OWS ~ ("name" | "id") ~ OWS ~ "(" ~ capture(Value) ~ ")" ~> ((value: String) ⇒ NameSelector(value))
  }

  def KindOperand = rule {
    OWS ~ ("kind" | "type") ~ OWS ~ "(" ~ capture(Value) ~ ")" ~> ((value: String) ⇒ KindSelector(value))
  }

  def NamespaceOperand = rule {
    OWS ~ ("namespace" | "group") ~ OWS ~ "(" ~ capture(Value) ~ ")" ~> ((value: String) ⇒ NamespaceSelector(value))
  }

  def ImageOperand = rule {
    OWS ~ "image" ~ OWS ~ "(" ~ capture(Value) ~ ")" ~> ((value: String) ⇒ ImageSelector(value))
  }

  def LabelOperand = rule {
    OWS ~ "label" ~ OWS ~ "(" ~ capture(Value) ~ ")" ~> ((value: String) ⇒ LabelSelector(value))
  }

  def IpOperand = rule {
    OWS ~ ("ip" | "host") ~ OWS ~ "(" ~ capture(Value) ~ ")" ~> ((value: String) ⇒ IpSelector(value))
  }

  def PortOperand = rule {
    OWS ~ "port" ~ OWS ~ "(" ~ capture(oneOrMore(CharPredicate.Digit)) ~ ")" ~> ((value: String) ⇒ PortSelector(value.toInt))
  }

  def Value = rule {
    ValuePart ~ optional('(' ~ ValuePart ~ ')' ~ ValuePart)
  }

  def ValuePart = rule {
    oneOrMore(CharPredicate.Printable -- '(' -- ')')
  }
}
