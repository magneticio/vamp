package io.vamp.common.config

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{ FlatSpec, Matchers }

@RunWith(classOf[JUnitRunner])
class ConfigSpec extends FlatSpec with Matchers {

  "Config" should "retrieve data from configuration" in {
    Config.load(Map("a.b.c" → 456, "q" → "qwerty", "f" → true))

    Config.int("a.b.c")() shouldBe 456
    Config.string("q")() shouldBe "qwerty"
    Config.boolean("f")() shouldBe true
  }
}
