package io.vamp.model.parser

import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class FilterConditionParserSpec extends FlatSpec with Matchers with FilterConditionParser {

  "FilterCondition" should "parse" in {
    parse("user-agent == a and user-agent != b") shouldBe {
      And(UserAgent("a"), Negation(UserAgent("b")))
    }
    parse("user-agent == a and not user-agent == b") shouldBe {
      And(UserAgent("a"), Negation(UserAgent("b")))
    }
  }

  it should "resolve user agent" in {
    parse("User-Agent==Firefox") shouldBe UserAgent("Firefox")
    parse("User-Agent== Firefox ") shouldBe UserAgent("Firefox")
    parse("User-Agent == Firefox ") shouldBe UserAgent("Firefox")
    parse(" User-Agent == Firefox ") shouldBe UserAgent("Firefox")
    parse(" User-Agent ! Firefox ") shouldBe Negation(UserAgent("Firefox"))
    parse(" User-Agent != Firefox ") shouldBe Negation(UserAgent("Firefox"))
  }

  it should "pass through non-matched" in {
    parse(" User-Agent == Fire fox ") shouldBe Value(" User-Agent == Fire fox ")
    parse("abc") shouldBe Value("abc")
  }
}
