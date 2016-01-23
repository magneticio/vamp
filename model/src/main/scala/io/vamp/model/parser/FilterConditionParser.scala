package io.vamp.model.parser

import org.parboiled.scala._

import scala.language.implicitConversions

trait FilterConditionParser extends Parser {

  //  import Ast._
  //
  //  def parse(expression: String): AstNode = TracingParseRunner(InputLine).run(expression).result match {
  //    case Some(node) ⇒ node
  //    case None       ⇒ NativeAcl(expression)
  //  }
  //
  //  def InputLine = rule {
  //    Expression ~ EOI
  //  }
  //
  //  def Expression: Rule1[AstNode] = rule {
  //    Term ~ zeroOrMore(
  //      (ignoreCase("or") | "|" | "||") ~ Term ~~> ((node1: AstNode, node2: AstNode) ⇒ Or(node1, node2))
  //    )
  //  }
  //
  //  def Term = rule {
  //    Factor ~ zeroOrMore(
  //      WhiteSpace ~ ignoreCase("and") /* | "&" | "&&"*/ ~ WhiteSpace ~ Factor ~~> ((node1: AstNode, node2: AstNode) ⇒ And(node1, node2))
  //    )
  //  }
  //
  //  def Factor = rule {
  //    //(optional(Not) ~> (_.nonEmpty) ~ (UserAgentExpression | Parenthesis)) ~~> ((negation: Boolean, node: Node) ⇒ if (negation) Negation(node) else node)
  //    UserAgentExpression // | Parenthesis
  //  }

  //  def Not = rule {
  //    "!" | ("not" ~ WhiteSpace)
  //  }
  //
  //  def Parenthesis: Rule1[Node] = rule {
  //    OptionalWhiteSpace ~ "(" ~ Expression ~ ")" ~ OptionalWhiteSpace ~~> ((node: Node) ⇒ node) ~ OptionalWhiteSpace
  //  }

  //  def UserAgentExpression: Rule1[AstNode] = rule {
  //    UserAgentString ~ (Equal | NonEqual) ~ String ~~> ((equal: Boolean, value: String) ⇒ if (equal) UserAgent(value) else Negation(UserAgent(value)))
  //  }
  //
  //  def UserAgentString = rule {
  //    OptionalWhiteSpace ~ ignoreCase("user") ~ optional("-") ~ ignoreCase("agent") ~ OptionalWhiteSpace
  //  }
  //
  //  def NonEqual = rule {
  //    ("!=" | "!") ~> (_ ⇒ false)
  //  }
  //
  //  def Equal = rule {
  //    ("==" | "=") ~> (_ ⇒ true)
  //  }
  //
  //  def String = rule {
  //    OptionalWhiteSpace ~ oneOrMore(noneOf(" \n\r\t\f")) ~> ((value: String) ⇒ value) ~ OptionalWhiteSpace
  //  }
  //
  //  def WhiteSpace = rule {
  //    oneOrMore(anyOf(" \n\r\t\f"))
  //  }
  //
  //  def OptionalWhiteSpace = rule {
  //    zeroOrMore(anyOf(" \n\r\t\f"))
  //  }

  //  val userAgent = "^(?i)user[-.]agent[ ]?([!])?=[ ]?([a-zA-Z0-9]+)$".r
  //  val host = "^(?i)host[ ]?([!])?=[ ]?([a-zA-Z0-9.]+)$".r
  //  val cookieContains = "^(?i)cookie (.+) contains (.+)$".r
  //  val hasCookie = "^(?i)has cookie (.+)$".r
  //  val missesCookie = "^(?i)misses cookie (.+)$".r
  //  val headerContains = "^(?i)header (.+) contains (.+)$".r
  //  val hasHeader = "^(?i)has header (.+)$".r
  //  val missesHeader = "^(?i)misses header (.+)$".r
  //  val rewrite = "^(?i)rewrite (.+) if (.+)$".r

  //  case userAgent(n, c)        ⇒ Condition(s"hdr_sub(user-agent) ${c.trim}", n == "!") :: Nil
  //  case host(n, c)             ⇒ Condition(s"hdr_str(host) ${c.trim}", n == "!") :: Nil
  //  case cookieContains(c1, c2) ⇒ Condition(s"cook_sub(${c1.trim}) ${c2.trim}") :: Nil
  //  case hasCookie(c)           ⇒ Condition(s"cook(${c.trim}) -m found") :: Nil
  //  case missesCookie(c)        ⇒ Condition(s"cook_cnt(${c.trim}) eq 0") :: Nil
  //  case headerContains(h, c)   ⇒ Condition(s"hdr_sub(${h.trim}) ${c.trim}") :: Nil
  //  case hasHeader(h)           ⇒ Condition(s"hdr_cnt(${h.trim}) gt 0") :: Nil
  //  case missesHeader(h)        ⇒ Condition(s"hdr_cnt(${h.trim}) eq 0") :: Nil
}
