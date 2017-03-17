package io.vamp.model.parser

import akka.parboiled2.{ ParserInput, Rule1 }

sealed trait ConditionDefinitionOperand extends Operand

case class Host(value: String) extends ConditionDefinitionOperand

case class Cookie(value: String) extends ConditionDefinitionOperand

case class Header(value: String) extends ConditionDefinitionOperand

case class UserAgent(value: String) extends ConditionDefinitionOperand

case class CookieContains(name: String, value: String) extends ConditionDefinitionOperand

case class HeaderContains(name: String, value: String) extends ConditionDefinitionOperand

trait ConditionDefinitionParser extends Parser[AstNode] {
  override def parser(expression: String) = new ConditionDefinitionParboiledParser(expression)
}

class ConditionDefinitionParboiledParser(override val input: ParserInput) extends BooleanParboiledParser(input) {

  override def Operand: Rule1[AstNode] = rule {
    HostOperand | UserAgentOperand | HeaderOperand | CookieOperand | CookieContainsOperand | HeaderContainsOperand | ValueOperand
  }

  override def ValueOperand = rule {
    "<" ~ capture(oneOrMore(noneOf("<>"))) ~ ">" ~> ((value: String) ⇒ Value(value.trim))
  }

  def HostOperand = rule {
    (HostString ~ ComparisonOperator ~ capture(String)) ~>
      ((equal: Boolean, value: String) ⇒ if (equal) Host(value) else Negation(Host(value)))
  }

  def HostString = rule {
    ignoreCase("host")
  }

  def UserAgentOperand = rule {
    (UserAgentString ~ ComparisonOperator ~ capture(String)) ~>
      ((equal: Boolean, value: String) ⇒ if (equal) UserAgent(value) else Negation(UserAgent(value)))
  }

  def UserAgentString = rule {
    ignoreCase("user") ~ optional(anyOf("-.")) ~ ignoreCase("agent")
  }

  def CookieOperand = rule {
    (ContainsOperator ~ CookieString ~ WS ~ capture(String)) ~>
      ((equal: Boolean, value: String) ⇒ if (equal) Cookie(value) else Negation(Cookie(value)))
  }

  def CookieContainsOperand = rule {
    (CookieString ~ WS ~ capture(String) ~ WS ~ ContainsOperator ~ capture(String)) ~>
      ((name: String, equal: Boolean, value: String) ⇒ if (equal) CookieContains(name, value) else Negation(CookieContains(name, value)))
  }

  def CookieString = rule {
    ignoreCase("cookie")
  }

  def HeaderOperand = rule {
    ContainsOperator ~ HeaderString ~ WS ~ capture(String) ~>
      ((equal: Boolean, value: String) ⇒ if (equal) Header(value) else Negation(Header(value)))
  }

  def HeaderContainsOperand = rule {
    (HeaderString ~ WS ~ capture(String) ~ WS ~ ContainsOperator ~ capture(String)) ~>
      ((name: String, equal: Boolean, value: String) ⇒ if (equal) HeaderContains(name, value) else Negation(HeaderContains(name, value)))
  }

  def HeaderString = rule {
    ignoreCase("header")
  }

  def ComparisonOperator: Rule1[Boolean] = rule {
    Equal | NonEqual | Has | Is | Misses
  }

  def ContainsOperator = rule {
    Has | Misses
  }

  def Is = rule {
    WS ~ ignoreCase("is") ~ WS ~> (() ⇒ true)
  }

  def Has = rule {
    OWS ~ (ignoreCase("has") | ignoreCase("contains")) ~ WS ~> (() ⇒ true)
  }

  def Misses = rule {
    OWS ~ ignoreCase("misses") ~ WS ~> (() ⇒ false)
  }

  def NonEqual = rule {
    ((OWS ~ ("!=" | "!") ~ OWS) | (WS ~ "not" ~ WS)) ~> (() ⇒ false)
  }

  def Equal = rule {
    OWS ~ ("==" | "=") ~ OWS ~> (() ⇒ true)
  }

  def String = rule {
    oneOrMore(noneOf(" \n\r\t\f()"))
  }
}
