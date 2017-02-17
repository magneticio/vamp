package io.vamp.model.parser

import akka.parboiled2.{ CharPredicate, ErrorFormatter, ParseError, Rule1 }

import scala.language.postfixOps
import scala.util.{ Failure, Success }

trait Parser[T] {

  def parse(expression: String): T = {
    val p = parser(expression)
    p.Input.run() match {
      case Success(node)          ⇒ node
      case Failure(e: ParseError) ⇒ throw new RuntimeException(s"'$expression'\n${p.formatError(e, new ErrorFormatter(showPosition = false))}")
      case Failure(_)             ⇒ throw new RuntimeException(s"'$expression'")
    }
  }

  def parser(expression: String): ParboiledParser[T]
}

trait ParboiledParser[T] extends akka.parboiled2.Parser {

  def Input: Rule1[T]

  def OWS = rule {
    WS ?
  }

  def WS = rule {
    CharPredicate(" \n\r\t\f") +
  }
}
