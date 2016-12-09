package io.vamp.model.parser

import akka.parboiled2._

import scala.language.postfixOps

trait BooleanParser extends Parser[AstNode] {
  def parser(expression: String) = new BooleanParboiledParser(expression)
}

class BooleanParboiledParser(val input: ParserInput) extends ParboiledParser[AstNode] {

  def Input = rule {
    Expression ~ EOI
  }

  def Expression: Rule1[AstNode] = rule {
    Term ~ zeroOrMore((ignoreCase("or") | "||" | "|") ~ WS ~ Term ~> Or)
  }

  def Term = rule {
    Factor ~ zeroOrMore((ignoreCase("and") | "&&" | "&") ~ WS ~ Factor ~> And)
  }

  def Factor = rule {
    OWS ~ (NotFactor | Operand | Parenthesis) ~ OWS
  }

  def NotFactor = rule {
    ((("!" ~ OWS) | ("not" ~ WS)) ~ (Operand | Parenthesis)) ~> Negation
  }

  def Parenthesis = rule {
    "(" ~ Expression ~ ")"
  }

  def Operand = rule {
    TrueConstant | FalseConstant | ValueOperand
  }

  def TrueConstant = rule {
    (ignoreCase("true") | ignoreCase("t") | "1") ~> (() ⇒ True)
  }

  def FalseConstant = rule {
    (ignoreCase("false") | ignoreCase("f") | "0") ~> (() ⇒ False)
  }

  def ValueOperand = rule {
    capture(noneOf(" \n\r\t\f()|&!") +) ~> Value
  }
}
