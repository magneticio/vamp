package io.vamp.model.parser

import org.parboiled.scala._

trait BooleanParser extends Parser {

  def parse(expression: String): AstNode = BasicParseRunner(InputLine).run(expression).result match {
    case Some(node) ⇒ node
    case None       ⇒ throw new RuntimeException(s"can't parse: $expression")
  }

  def InputLine = rule {
    Expression ~ EOI
  }

  def Expression: Rule1[AstNode] = rule {
    Term ~ zeroOrMore(
      OptionalWhiteSpace ~ (ignoreCase("or") | "||" | "|") ~ WhiteSpace ~ Term ~~> ((node1: AstNode, node2: AstNode) ⇒ Or(node1, node2))
    ) ~ OptionalWhiteSpace
  }

  def Term: Rule1[AstNode] = rule {
    Factor ~ zeroOrMore(
      OptionalWhiteSpace ~ (ignoreCase("and") | "&&" | "&") ~ WhiteSpace ~ Factor ~~> ((node1: AstNode, node2: AstNode) ⇒ And(node1, node2))
    )
  }

  def Factor: Rule1[AstNode] = rule {
    (optional(Not) ~> (_.nonEmpty) ~ (Operand | Parenthesis)) ~~> ((negation: Boolean, node: AstNode) ⇒ if (negation) Negation(node) else node)
  }

  def Operand: Rule1[AstNode] = rule {
    TrueConstant | FalseConstant | ValueOperand
  }

  def Not = rule {
    OptionalWhiteSpace ~ ("!" | ("not" ~ WhiteSpace))
  }

  def Parenthesis: Rule1[AstNode] = rule {
    OptionalWhiteSpace ~ "(" ~ Expression ~ ")" ~~> ((node: AstNode) ⇒ node)
  }

  def TrueConstant: Rule1[AstNode] = rule {
    OptionalWhiteSpace ~ (((ignoreCase("true") | ignoreCase("t")) ~ (OptionalWhiteSpace | EOI)) | "1") ~> (_ ⇒ True)
  }

  def FalseConstant: Rule1[AstNode] = rule {
    OptionalWhiteSpace ~ (((ignoreCase("false") | ignoreCase("f")) ~ (OptionalWhiteSpace | EOI)) | "0") ~> (_ ⇒ False)
  }

  def ValueOperand: Rule1[AstNode] = rule {
    OptionalWhiteSpace ~ oneOrMore(noneOf(" \n\r\t\f()|&!")) ~> ((value: String) ⇒ Value(value))
  }

  def WhiteSpace = rule {
    oneOrMore(anyOf(" \n\r\t\f"))
  }

  def OptionalWhiteSpace = rule {
    zeroOrMore(anyOf(" \n\r\t\f"))
  }
}
