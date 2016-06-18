package io.vamp.common.config

import com.typesafe.config.ConfigFactory
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{ FlatSpec, Matchers }

@RunWith(classOf[JUnitRunner])
class ConfigSpec extends FlatSpec with Matchers {

  "Config" should "retrieve data from configuration" in {

    val config = new Config(ConfigFactory.parseString("a.b.c=456, q=qwerty"), "")

    config.int("a.b.c") shouldBe 456
    config.string("q") shouldBe "qwerty"
  }

  it should "override values from environment variables" in {

    sys.env.collect {
      case (e, v) if e.forall(_.isUpper) ⇒

        val param = e.toLowerCase
        val config = new Config(ConfigFactory.parseString(s"a.b.c=456, $param=qwerty"), "")

        config.int("a.b.c") shouldBe 456
        config.string(param) shouldBe v
    }
  }
}
