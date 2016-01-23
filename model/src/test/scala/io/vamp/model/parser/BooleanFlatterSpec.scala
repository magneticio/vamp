package io.vamp.model.parser

import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class BooleanFlatterSpec extends FlatSpec with Matchers with BooleanFlatter with BooleanParser {

  "BooleanMapper" should "map 'and'" in {
    transform(parse("a and b")) shouldBe parse("a and b")
    transform(parse("!a and b")) shouldBe parse("!a and b")
    transform(parse("!a and b and !c")) shouldBe parse("!a and b and !c")

    transform(parse("true and true")) shouldBe parse("true")
    transform(parse("1 and 0")) shouldBe parse("false")
    transform(parse("F and F")) shouldBe parse("false")
  }

  it should "map combined" in {
    transform(parse("a or b")) shouldBe parse("(a and !b) or (!a and b) or (a and b)")
    transform(parse("(a or b) and c")) shouldBe parse("(a and !b and c) or (!a and b and c) or (a and b and c)")
    transform(parse("(a or !b) and c")) shouldBe parse("(!a and !b and c) or (a and !b and c) or (a and b and c)")
  }

  it should "reduce" in {
    flatten(parse("a or a")) shouldBe parse("a")
    flatten(parse("a or !a")) shouldBe parse("true")
    flatten(parse("a and a")) shouldBe parse("a")
    flatten(parse("a and !a")) shouldBe parse("false")

    flatten(parse("a or b")) shouldBe parse("a or b")
    flatten(parse("a and b")) shouldBe parse("a and b")
    flatten(parse("a or !b")) shouldBe parse("a or !b")
    flatten(parse("(a or b) and c")) shouldBe parse("(a and c) or (b and c)")
    flatten(parse("(a or !b) and c")) shouldBe parse("(!b and c) or (a and c)")

    flatten(parse("true or true")) shouldBe parse("1")
    flatten(parse("1 or 0")) shouldBe parse("T")
    flatten(parse("F or F")) shouldBe parse("false")

    flatten(parse("a or true")) shouldBe parse("true")
    flatten(parse("a or false")) shouldBe parse("a")

    flatten(parse("a and true")) shouldBe parse("a")
    flatten(parse("a and false")) shouldBe parse("0")

    flatten(parse("a or true and b")) shouldBe parse("a or b")
    flatten(parse("a or false and b")) shouldBe parse("a")

    flatten(parse("a and true or b")) shouldBe parse("a or b")
    flatten(parse("a and false or b")) shouldBe parse("b")
  }

  private def transform(node: AstNode): AstNode = {
    val terms = map(node)
    if (terms.nonEmpty) {
      terms map {
        _.terms.reduce {
          (op1, op2) ⇒ And(op1, op2)
        }
      } reduce {
        (op1, op2) ⇒ Or(op1, op2)
      }
    } else False
  }
}
