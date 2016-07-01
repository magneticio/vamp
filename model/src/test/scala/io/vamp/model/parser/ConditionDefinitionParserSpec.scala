package io.vamp.model.parser

import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ConditionDefinitionParserSpec extends FlatSpec with Matchers with ConditionDefinitionParser {

  "ConditionDefinition" should "parse" in {
    parse("user-agent == a and user-agent != b") shouldBe {
      And(UserAgent("a"), Negation(UserAgent("b")))
    }

    parse("user-agent == a and not user-agent == b") shouldBe {
      And(UserAgent("a"), Negation(UserAgent("b")))
    }
  }

  it should "resolve direct value" in {
    parse("<water ⇒ ice>") shouldBe Value("water ⇒ ice")
    parse("< water ⇒ ice>") shouldBe Value("water ⇒ ice")
    parse("< water ⇒ ice >") shouldBe Value("water ⇒ ice")
    parse(" <water ⇒ ice>") shouldBe Value("water ⇒ ice")
    parse("<water ⇒ ice> ") shouldBe Value("water ⇒ ice")
    parse(" <water ⇒ ice> ") shouldBe Value("water ⇒ ice")
    parse("  <  water  ⇒  ice  > ") shouldBe Value("water  ⇒  ice")
  }

  it should "resolve user agent" in {
    parse("User-Agent==Firefox") shouldBe UserAgent("Firefox")
    parse("User-Agent== Firefox ") shouldBe UserAgent("Firefox")
    parse("User-Agent == Firefox ") shouldBe UserAgent("Firefox")
    parse(" User-Agent == Firefox ") shouldBe UserAgent("Firefox")
    parse(" User-Agent ! Firefox ") shouldBe Negation(UserAgent("Firefox"))
    parse(" User-Agent != Firefox ") shouldBe Negation(UserAgent("Firefox"))
    parse("user.agent != Firefox") shouldBe Negation(UserAgent("Firefox"))
    parse("User-Agent is Firefox") shouldBe UserAgent("Firefox")
    parse("User-Agent not Firefox") shouldBe Negation(UserAgent("Firefox"))
    parse(" ( User-Agent == Firefox ) ") shouldBe UserAgent("Firefox")
  }

  it should "resolve host" in {
    parse("host == localhost") shouldBe Host("localhost")
    parse("host != localhost") shouldBe Negation(Host("localhost"))

    parse("host is localhost") shouldBe Host("localhost")
    parse("host not localhost") shouldBe Negation(Host("localhost"))
    parse("host misses localhost") shouldBe Negation(Host("localhost"))

    parse("host has localhost") shouldBe Host("localhost")
    parse("host contains localhost") shouldBe Host("localhost")

    parse("! host is localhost") shouldBe Negation(Host("localhost"))
    parse("not host is localhost") shouldBe Negation(Host("localhost"))
  }

  it should "resolve cookie" in {
    parse("has cookie vamp") shouldBe Cookie("vamp")
    parse("misses cookie vamp") shouldBe Negation(Cookie("vamp"))
    parse("contains cookie vamp") shouldBe Cookie("vamp")
  }

  it should "resolve header" in {
    parse("has header vamp") shouldBe Header("vamp")
    parse("misses header vamp") shouldBe Negation(Header("vamp"))
    parse("contains header vamp") shouldBe Header("vamp")
  }

  it should "resolve cookie contains" in {
    parse("cookie vamp has 12345") shouldBe CookieContains("vamp", "12345")
    parse("cookie vamp misses 12345") shouldBe Negation(CookieContains("vamp", "12345"))
    parse("cookie vamp contains 12345") shouldBe CookieContains("vamp", "12345")
  }

  it should "resolve header contains" in {
    parse("header vamp has 12345") shouldBe HeaderContains("vamp", "12345")
    parse("header vamp misses 12345") shouldBe Negation(HeaderContains("vamp", "12345"))
    parse("header vamp contains 12345") shouldBe HeaderContains("vamp", "12345")
  }
}
