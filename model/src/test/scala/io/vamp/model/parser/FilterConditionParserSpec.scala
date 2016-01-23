package io.vamp.model.parser

import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class FilterConditionParserSpec extends FlatSpec with Matchers with FilterConditionParser {

  //  import Ast._
  //
  //  "FilterCondition" should "parse" in {
  //    parse("user-agent==a and user-agent==b") should be {
  //      And(UserAgent("a"), UserAgent("b"))
  //    }
  //  }
  //
  //  it should "resolve user agent" in {
  //    parse("User-Agent==Firefox") should be(UserAgent("Firefox"))
  //    parse("User-Agent== Firefox ") should be(UserAgent("Firefox"))
  //    parse("User-Agent == Firefox ") should be(UserAgent("Firefox"))
  //    parse(" User-Agent == Firefox ") should be(UserAgent("Firefox"))
  //    parse(" User-Agent ! Firefox ") should be(Negation(UserAgent("Firefox")))
  //    parse(" User-Agent != Firefox ") should be(Negation(UserAgent("Firefox")))
  //  }
  //
  //  it should "pass through non-matched" in {
  //    parse(" User-Agent == Fire fox ") should be(NativeAcl(" User-Agent == Fire fox "))
  //    parse("abc") should be(NativeAcl("abc"))
  //  }
}
