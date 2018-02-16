package io.vamp.model.parser

import akka.parboiled2.{ CharPredicate, ParserInput, Rule0, Rule1 }

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

  private val QUOTED = CharPredicate.Printable -- '"' -- '\''

  private val UNQUOTED = CharPredicate.Visible -- '(' -- ')'

  private val sb = new java.lang.StringBuilder

  override def Operand: Rule1[AstNode] = rule {
    HostOperand | UserAgentOperand | HeaderOperand | CookieOperand | CookieContainsOperand | HeaderContainsOperand | ValueOperand
  }

  override def ValueOperand = rule {
    "<" ~ capture(oneOrMore(noneOf("<>"))) ~ ">" ~> ((value: String) ⇒ Value(value.trim))
  }

  def HostOperand = rule {
    (HostString ~ ComparisonOperator ~ String) ~>
      ((equal: Boolean, value: String) ⇒ if (equal) Host(value) else Negation(Host(value)))
  }

  def HostString = rule {
    ignoreCase("host")
  }

  def UserAgentOperand = rule {
    (UserAgentString ~ ComparisonOperator ~ String) ~>
      ((equal: Boolean, value: String) ⇒ if (equal) UserAgent(value) else Negation(UserAgent(value)))
  }

  def UserAgentString = rule {
    ignoreCase("user") ~ optional(anyOf("-.")) ~ ignoreCase("agent")
  }

  def CookieOperand = rule {
    (ContainsOperator ~ CookieString ~ WS ~ String) ~>
      ((equal: Boolean, value: String) ⇒ if (equal) Cookie(value) else Negation(Cookie(value)))
  }

  def CookieContainsOperand = rule {
    (CookieString ~ WS ~ String ~ WS ~ ContainsOperator ~ String) ~>
      ((name: String, equal: Boolean, value: String) ⇒ if (equal) CookieContains(name, value) else Negation(CookieContains(name, value)))
  }

  def CookieString = rule {
    ignoreCase("cookie")
  }

  def HeaderOperand = rule {
    ContainsOperator ~ HeaderString ~ WS ~ String ~>
      ((equal: Boolean, value: String) ⇒ if (equal) Header(value) else Negation(Header(value)))
  }

  def HeaderContainsOperand = rule {
    (HeaderString ~ WS ~ String ~ WS ~ ContainsOperator ~ String) ~>
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
    SingleQuoted | DoubleQuoted | capture(oneOrMore(UNQUOTED))
  }

  def SingleQuoted = rule {
    '\'' ~ clearSB() ~ zeroOrMore((QUOTED | "''") ~ appendSB()) ~ '\'' ~ push(sb.toString)
  }

  def DoubleQuoted = rule {
    '"' ~ clearSB() ~ zeroOrMore((QUOTED | "\"\"") ~ appendSB()) ~ '"' ~ push(sb.toString)
  }

  private def clearSB(): Rule0 = rule {
    run(sb.setLength(0))
  }

  private def appendSB(): Rule0 = rule {
    run(sb.append(lastChar))
  }
}
