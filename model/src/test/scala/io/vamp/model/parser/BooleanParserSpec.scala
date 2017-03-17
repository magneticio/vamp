package io.vamp.model.parser

import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BooleanParserSpec extends FlatSpec with Matchers with BooleanParser {

  "BooleanParser" should "parse operands" in {
    parse("a") shouldBe Value("a")
    parse(" a") shouldBe Value("a")
    parse("a ") shouldBe Value("a")
    parse(" a ") shouldBe Value("a")
    parse("((a))") shouldBe Value("a")

    parse("TRUE") shouldBe True
    parse("t ") shouldBe True
    parse(" 1 ") shouldBe True

    parse("False") shouldBe False
    parse(" F") shouldBe False
    parse(" 0 ") shouldBe False
    parse("(false)") shouldBe False
    parse("((1))") shouldBe True
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

    parse("true and true") shouldBe And(True, True)
    parse("1 and 0") shouldBe And(True, False)
    parse("F && F") shouldBe And(False, False)

    parse("a and true") shouldBe And(Value("a"), True)
    parse("a & false") shouldBe And(Value("a"), False)
  }

  it should "parse 'or' expression" in {
    parse("a or b") shouldBe Or(Value("a"), Value("b"))
    parse("a || b") shouldBe Or(Value("a"), Value("b"))
    parse("a | b") shouldBe Or(Value("a"), Value("b"))
    parse(" a or  b ") shouldBe Or(Value("a"), Value("b"))
    parse("a Or b | c") shouldBe Or(Or(Value("a"), Value("b")), Value("c"))

    parse("true or true") shouldBe Or(True, True)
    parse("1 || 0") shouldBe Or(True, False)
    parse("F | F") shouldBe Or(False, False)

    parse("a or true") shouldBe Or(Value("a"), True)
    parse("a or false") shouldBe Or(Value("a"), False)
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
    parse("(a or b) and c") shouldBe And(Or(Value("a"), Value("b")), Value("c"))
    parse("a or (b and c)") shouldBe Or(Value("a"), And(Value("b"), Value("c")))
  }
}
