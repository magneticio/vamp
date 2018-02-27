package io.vamp.model.parser

import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RouteSelectorParserSpec extends FlatSpec with Matchers {

  val parser = new RouteSelectorParser()

  "RouteSelectorParser" should "parse" in {
    parser.parse("id(.*)") shouldBe {
      IdSelector(".*")
    }

    parser.parse("image (^.*$)") shouldBe {
      ImageSelector("^.*$")
    }

    parser.parse("label(water)") shouldBe {
      LabelSelector("water")
    }

    parser.parse("label(^water:(.*)$)") shouldBe {
      LabelSelector("^water:(.*)$")
    }
  }

  it should "parse an expression" in {
    parser.parse("id(water) and label(ice)") shouldBe {
      And(IdSelector("water"), LabelSelector("ice"))
    }

    parser.parse("id(water) or not label(ice)") shouldBe {
      Or(IdSelector("water"), Negation(LabelSelector("ice")))
    }

    parser.parse("(id(water) and not label(ice)) or image(solid)") shouldBe {
      Or(And(IdSelector("water"), Negation(LabelSelector("ice"))), ImageSelector("solid"))
    }

    parser.parse("id(^water:(.*)$) or label(^ice:(.*)$)") shouldBe {
      Or(IdSelector("^water:(.*)$"), LabelSelector("^ice:(.*)$"))
    }
  }

  it should "fail if expression is invalid" in {
    the[RuntimeException] thrownBy parser.parse("winter(cold)")
  }
}
