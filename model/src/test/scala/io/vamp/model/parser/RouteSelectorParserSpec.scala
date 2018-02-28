package io.vamp.model.parser

import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class RouteSelectorParserSpec extends FlatSpec with Matchers {

  val parser = new RouteSelectorParser()

  "RouteSelectorParser" should "parse" in {
    parser.parse("name(.*)") shouldBe {
      NameSelector(".*")
    }

    parser.parse("id(.*)") shouldBe {
      NameSelector(".*")
    }

    parser.parse("kind(app)") shouldBe {
      KindSelector("app")
    }

    parser.parse("type(pod)") shouldBe {
      KindSelector("pod")
    }

    parser.parse("namespace(default)") shouldBe {
      NamespaceSelector("default")
    }

    parser.parse("group(vamp)") shouldBe {
      NamespaceSelector("vamp")
    }

    parser.parse("image (^.*$)") shouldBe {
      ImageSelector("^.*$")
    }

    parser.parse("ip(127.0.0.1)") shouldBe {
      IpSelector("127.0.0.1")
    }

    parser.parse("host(localhost)") shouldBe {
      IpSelector("localhost")
    }

    parser.parse("port(8080)") shouldBe {
      PortSelector(8080)
    }

    parser.parse("label(water)(ice)") shouldBe {
      LabelSelector("water", "ice")
    }

    parser.parse("label (water) (ice) ") shouldBe {
      LabelSelector("water", "ice")
    }

    parser.parse("index(0)") shouldBe {
      PortIndexSelector(0)
    }

    parser.parse("port_index(0)") shouldBe {
      PortIndexSelector(0)
    }
  }

  it should "parse with single group" in {
    parser.parse("image(^water:(.*)$)") shouldBe {
      ImageSelector("^water:(.*)$")
    }

    parser.parse("image((.*)$)") shouldBe {
      ImageSelector("(.*)$")
    }

    parser.parse("image(^(.*))") shouldBe {
      ImageSelector("^(.*)")
    }

    parser.parse("image((.*))") shouldBe {
      ImageSelector("(.*)")
    }
  }

  it should "parse an expression" in {
    parser.parse("id(water) and image(ice)") shouldBe {
      And(NameSelector("water"), ImageSelector("ice"))
    }

    parser.parse("id(water) or not image(ice)") shouldBe {
      Or(NameSelector("water"), Negation(ImageSelector("ice")))
    }

    parser.parse("(id(water) and not label(water)(ice)) or image(solid)") shouldBe {
      Or(And(NameSelector("water"), Negation(LabelSelector("water", "ice"))), ImageSelector("solid"))
    }

    parser.parse("id(^water:(.*)$) or image(^ice:(.*)$)") shouldBe {
      Or(NameSelector("^water:(.*)$"), ImageSelector("^ice:(.*)$"))
    }
  }

  it should "fail if expression is invalid" in {
    the[RuntimeException] thrownBy parser.parse("winter(cold)")
  }
}
