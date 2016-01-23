package io.vamp.model.parser

import org.parboiled.scala._

import scala.language.implicitConversions

case class UserAgent(value: String) extends Operand

trait FilterConditionParser extends BooleanParser {

  override def parse(expression: String): AstNode = BasicParseRunner(InputLine).run(expression).result match {
    case Some(node) ⇒ node
    case None       ⇒ Value(expression)
  }

  override def Operand: Rule1[AstNode] = rule {
    UserAgentOperand | super.Operand
  }

  def UserAgentOperand = rule {
    UserAgentString ~ (Equal | NonEqual) ~ String ~~> ((equal: Boolean, value: String) ⇒ if (equal) UserAgent(value) else Negation(UserAgent(value)))
  }

  def UserAgentString = rule {
    OptionalWhiteSpace ~ ignoreCase("user") ~ optional("-") ~ ignoreCase("agent") ~ OptionalWhiteSpace
  }

  def NonEqual = rule {
    ("!=" | "!") ~> (_ ⇒ false)
  }

  def Equal = rule {
    ("==" | "=") ~> (_ ⇒ true)
  }

  def String = rule {
    OptionalWhiteSpace ~ oneOrMore(noneOf(" \n\r\t\f")) ~> (value ⇒ value)
  }

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
