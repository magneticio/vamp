package io.vamp.model.parser

import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RouteSelectorParserSpec extends FlatSpec with Matchers with RouteSelectorParser {

  "RouteSelectorParser" should "parse" in {
    parse("id(.*)") shouldBe {
      IdSelector(".*")
    }

    parse("image (^.*$)") shouldBe {
      ImageSelector("^.*$")
    }

    parse("label(water)") shouldBe {
      LabelSelector("water")
    }

    parse("label(^water:(.*)$)") shouldBe {
      LabelSelector("^water:(.*)$")
    }
  }

  it should "parse an expression" in {
    parse("id(water) and label(ice)") shouldBe {
      And(IdSelector("water"), LabelSelector("ice"))
    }

    parse("id(water) or not label(ice)") shouldBe {
      Or(IdSelector("water"), Negation(LabelSelector("ice")))
    }

    parse("(id(water) and not label(ice)) or image(solid)") shouldBe {
      Or(And(IdSelector("water"), Negation(LabelSelector("ice"))), ImageSelector("solid"))
    }

    parse("id(^water:(.*)$) or label(^ice:(.*)$)") shouldBe {
      Or(IdSelector("^water:(.*)$"), LabelSelector("^ice:(.*)$"))
    }
  }
}
