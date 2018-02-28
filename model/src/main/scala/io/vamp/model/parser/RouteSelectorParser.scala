package io.vamp.model.parser

import akka.parboiled2._

import scala.util.matching.Regex

sealed trait RouteSelectorOperand[T] extends Operand {

  def matches(input: T): Boolean
}

trait RouteRegExpSelectorOperand extends RouteSelectorOperand[String] {

  def value: String

  private val valueMatcher: Regex = s"$value".r

  def matches(input: String): Boolean = input match {
    case valueMatcher(_*) ⇒ true
    case _                ⇒ false
  }
}

case class NameSelector(value: String) extends RouteRegExpSelectorOperand

case class KindSelector(value: String) extends RouteRegExpSelectorOperand

case class NamespaceSelector(value: String) extends RouteRegExpSelectorOperand

case class ImageSelector(value: String) extends RouteRegExpSelectorOperand

case class LabelSelector(name: String, value: String) extends RouteSelectorOperand[(String, String)] {

  private val nameMatcher: Regex = s"$name".r

  private val valueMatcher: Regex = s"$value".r

  def matches(input: (String, String)): Boolean = {
    (input._1, input._2) match {
      case (nameMatcher(_*), valueMatcher(_*)) ⇒ true
      case _                                   ⇒ false
    }
  }
}

case class IpSelector(value: String) extends RouteRegExpSelectorOperand

case class PortSelector(port: Int) extends RouteSelectorOperand[Int] {
  def matches(input: Int): Boolean = input == port
}

case class PortIndexSelector(index: Int) extends RouteSelectorOperand[Int] {
  def matches(input: Int): Boolean = false
}

class RouteSelectorParser extends Parser[AstNode] {
  override def parser(expression: String) = new RouteSelectorParboiledParser(expression)
}

class RouteSelectorParboiledParser(override val input: ParserInput) extends BooleanParboiledParser(input) {

  override def Operand: Rule1[AstNode] = rule {
    NamespaceOperand | NameOperand | KindOperand | ImageOperand | LabelOperand | IpOperand | PortOperand | PortIndexOperand | TrueConstant | FalseConstant
  }

  private def NameOperand = rule {
    OWS ~ ("name" | "id") ~ OWS ~ "(" ~ capture(Value) ~ ")" ~> ((value: String) ⇒ NameSelector(value))
  }

  private def KindOperand = rule {
    OWS ~ ("kind" | "type") ~ OWS ~ "(" ~ capture(Value) ~ ")" ~> ((value: String) ⇒ KindSelector(value))
  }

  private def NamespaceOperand = rule {
    OWS ~ ("namespace" | "group") ~ OWS ~ "(" ~ capture(Value) ~ ")" ~> ((value: String) ⇒ NamespaceSelector(value))
  }

  private def ImageOperand = rule {
    OWS ~ "image" ~ OWS ~ "(" ~ capture(Value) ~ ")" ~> ((value: String) ⇒ ImageSelector(value))
  }

  private def LabelOperand = rule {
    OWS ~ "label" ~ OWS ~ "(" ~ capture(Value) ~ ")" ~ OWS ~ "(" ~ capture(Value) ~ ")" ~> ((name: String, value: String) ⇒ LabelSelector(name, value))
  }

  private def IpOperand = rule {
    OWS ~ ("ip" | "host") ~ OWS ~ "(" ~ capture(Value) ~ ")" ~> ((value: String) ⇒ IpSelector(value))
  }

  private def PortOperand = rule {
    OWS ~ "port" ~ OWS ~ "(" ~ capture(oneOrMore(CharPredicate.Digit)) ~ ")" ~> ((value: String) ⇒ PortSelector(value.toInt))
  }

  private def PortIndexOperand = rule {
    OWS ~ ("port_index" | "index") ~ OWS ~ "(" ~ capture(oneOrMore(CharPredicate.Digit)) ~ ")" ~> ((value: String) ⇒ PortIndexSelector(value.toInt))
  }

  private def Value = rule {
    optional(ValuePart) ~ '(' ~ ValuePart ~ ')' ~ optional(ValuePart) | ValuePart
  }

  private def ValuePart = rule {
    oneOrMore(CharPredicate.Printable -- '(' -- ')')
  }
}
