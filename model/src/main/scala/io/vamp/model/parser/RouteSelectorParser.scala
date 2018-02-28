package io.vamp.model.parser

import akka.parboiled2._

import scala.util.matching.Regex

sealed trait RouteSelectorOperand[T] extends Operand {

  def matches(input: T): Either[Boolean, String]
}

trait RouteRegExpSelectorOperand extends RouteSelectorOperand[String] {

  def value: String

  private val valueMatcher: Regex = s"$value".r

  def matches(input: String): Either[Boolean, String] = input match {
    case valueMatcher(group) ⇒ Right(group)
    case valueMatcher(_*)    ⇒ Left(true)
    case _                   ⇒ Left(false)
  }
}

case class NameSelector(value: String) extends RouteRegExpSelectorOperand

case class KindSelector(value: String) extends RouteRegExpSelectorOperand

case class NamespaceSelector(value: String) extends RouteRegExpSelectorOperand

case class ImageSelector(value: String) extends RouteRegExpSelectorOperand

case class LabelSelector(name: String, value: String) extends RouteSelectorOperand[(String, String)] {

  private val nameMatcher: Regex = s"$name".r

  private val valueMatcher: Regex = s"$value".r

  def matches(input: (String, String)): Either[Boolean, String] = {
    val name = input._1 match {
      case nameMatcher(group) ⇒ Right(group)
      case nameMatcher(_*)    ⇒ Left(true)
      case _                  ⇒ Left(false)
    }
    val value = input._2 match {
      case valueMatcher(group) ⇒ Right(group)
      case valueMatcher(_*)    ⇒ Left(true)
      case _                   ⇒ Left(false)
    }
    (name, value) match {
      case (Right(n), Right(v))     ⇒ Right(s"$n=$v")
      case (Right(n), Left(true))   ⇒ Right(s"$n")
      case (Left(true), Right(v))   ⇒ Right(s"$v")
      case (Left(true), Left(true)) ⇒ Left(true)
      case _                        ⇒ Left(false)
    }
  }
}

case class IpSelector(value: String) extends RouteRegExpSelectorOperand

case class PortSelector(port: Int) extends RouteSelectorOperand[Int] {
  def matches(input: Int): Either[Boolean, String] = Left(input == port)
}

case class PortIndexSelector(index: Int) extends RouteSelectorOperand[Int] {
  def matches(input: Int): Either[Boolean, String] = Left(index == input)
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
