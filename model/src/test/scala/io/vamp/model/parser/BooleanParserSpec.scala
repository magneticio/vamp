package io.vamp.model.parser

import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class BooleanParserSpec extends FlatSpec with Matchers with BooleanParser {

  "BooleanParser" should "parse simple values" in {
    parse("a") shouldBe Value("a")
    parse(" a") shouldBe Value("a")
    parse("a ") shouldBe Value("a")
    parse(" a ") shouldBe Value("a")
  }

  it should "parse 'not' expression" in {
    parse("not a") shouldBe Negation(Value("a"))
    parse("! a") shouldBe Negation(Value("a"))
    parse(" not a ") shouldBe Negation(Value("a"))
  }

  it should "parse 'and' expression" in {
    parse("a and b") shouldBe And(Value("a"), Value("b"))
    parse("a && b") shouldBe And(Value("a"), Value("b"))
    parse("a & b") shouldBe And(Value("a"), Value("b"))
    parse(" a & b ") shouldBe And(Value("a"), Value("b"))
    parse("a AND b And c") shouldBe And(And(Value("a"), Value("b")), Value("c"))
  }

  it should "parse 'or' expression" in {
    parse("a or b") shouldBe Or(Value("a"), Value("b"))
    parse("a || b") shouldBe Or(Value("a"), Value("b"))
    parse("a | b") shouldBe Or(Value("a"), Value("b"))
    parse(" a or  b ") shouldBe Or(Value("a"), Value("b"))
    parse("a Or b | c") shouldBe Or(Or(Value("a"), Value("b")), Value("c"))
  }

  it should "parse parenthesis expression" in {
    parse("((a))") shouldBe Value("a")
    parse("(a and b)") shouldBe And(Value("a"), Value("b"))
    parse("(a and b) and c") shouldBe And(And(Value("a"), Value("b")), Value("c"))
    parse("a and (b and c)") shouldBe And(Value("a"), And(Value("b"), Value("c")))
  }

  it should "parse combined expression" in {
    parse("a and b or c") shouldBe Or(And(Value("a"), Value("b")), Value("c"))
    parse("a or b and c") shouldBe Or(Value("a"), And(Value("b"), Value("c")))
    parse("!(a and b) and c") shouldBe And(Negation(And(Value("a"), Value("b"))), Value("c"))
  }
}
