package io.vamp.config

import org.scalatest._

import scala.concurrent.duration.{ FiniteDuration, MILLISECONDS }

import io.vamp.config.ConfigReader._
import cats.data.Validated._

class ConfigReaderSpec extends FlatSpec with Matchers {

  "A ConfigReader" should "Read a value of type String" in {
    ConfigReader.stringConfigReader.read("string.test").shouldBe(valid("string-test"))
  }

  it should "Read a value of type List[String]" in {
    ConfigReader.stringListConfigReader.read("string.list").shouldEqual(valid(List("1", "2", "3")))
  }

  it should "Read a value of type Boolean" in {
    ConfigReader.booleanConfigReader.read("boolean.test").shouldBe(valid(true))
  }

  it should "Read a value of type List[Boolean]" in {
    ConfigReader.booleanListConfigReader.read("boolean.list").shouldBe(valid(List(true, true, false, false)))
  }

  it should "Read a value of type Int" in {
    ConfigReader.intConfigReader.read("int.test").shouldBe(valid(7))
  }

  it should "Read a value of type List[Int]" in {
    ConfigReader.intListConfigReader.read("int.list").shouldBe(valid(List(1, 2, 3)))
  }

  it should "Read a value of type Double" in {
    ConfigReader.doubleConfigReader.read("double.test").shouldBe(valid(7.77))
  }

  it should "Read a value of type List[Double]" in {
    ConfigReader.doubleListConfigReader.read("double.list").shouldBe(valid(List(1.1, 2.2, 3.3)))
  }

  it should "Read a value of type Long" in {
    ConfigReader.longConfigReader.read("long.test").shouldBe(valid(7.toLong))
  }

  it should "Read a value of type List[Long]" in {
    ConfigReader.longListConfigReader.read("long.list").shouldBe(valid(List[Long](1L, 2L, 3L)))
  }

  it should "Read a value of type FiniteDuration" in {
    ConfigReader.durationConfigReader
      .read("duration.test")
      .shouldBe(valid(FiniteDuration(1, MILLISECONDS)))
  }

  it should "Read a value of type Option[FiniteDuration]" in {
    ConfigReader.durationListConfigReader
      .read("duration.list")
      .shouldBe(valid(List(FiniteDuration(1, MILLISECONDS), FiniteDuration(2, MILLISECONDS))))
  }

  it should "Read a value of type Option[A], for Some of any A" in {
    ConfigReader
      .optionalConfigReader[String].read("option.test")
      .shouldBe(valid(Some("exists")))
  }

  it should "Read a value of type Option[A], for None of any A" in {
    ConfigReader
      .optionalConfigReader[String].read("none.test")
      .shouldBe(valid(None))
  }

  it should "Read any case class that has it data types defined" in {
    case class ConfigTest(test: String, dude: Boolean)

    val outcome = ConfigTest("test", true)

    val config = ConfigReader[ConfigTest].read("config")
    println(config)

    config.shouldEqual(valid(outcome))
  }

}
