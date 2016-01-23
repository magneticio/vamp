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
  }

  it should "map combined" in {
    transform(parse("a or b")) shouldBe parse("(a and !b) or (!a and b) or (a and b)")
    transform(parse("(a or b) and c")) shouldBe parse("(a and !b and c) or (!a and b and c) or (a and b and c)")
    transform(parse("(a or !b) and c")) shouldBe parse("(!a and !b and c) or (a and !b and c) or (a and b and c)")
  }

  it should "reduce" in {
    flatten(parse("a or a")) shouldBe parse("a")
    flatten(parse("a or !a")) shouldBe parse("a or !a") // can't reduce to nothing, no "true" or "false"
    flatten(parse("a and a")) shouldBe parse("a")
    flatten(parse("a and !a")) shouldBe parse("a and !a")

    flatten(parse("a or b")) shouldBe parse("a or b")
    flatten(parse("a and b")) shouldBe parse("a and b")
    flatten(parse("a or !b")) shouldBe parse("a or !b")
    flatten(parse("(a or b) and c")) shouldBe parse("(a and c) or (b and c)")
    flatten(parse("(a or !b) and c")) shouldBe parse("(!b and c) or (a and c)")
  }

  private def transform(node: AstNode): AstNode = map(node) map {
    _.terms.reduce {
      (op1, op2) ⇒ And(op1, op2)
    }
  } reduce {
    (op1, op2) ⇒ Or(op1, op2)
  }
}
