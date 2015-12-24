package io.vamp.model.reader

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{ FlatSpec, Matchers }

import scala.util.{ Failure, Success }

@RunWith(classOf[JUnitRunner])
class UnitValueSpec extends FlatSpec with Matchers {

  "Percentage" should "parse" in {

    UnitValue.of[Percentage]("0 %") shouldBe Success(Percentage(0))
    UnitValue.of[Percentage](" 50 %") shouldBe Success(Percentage(50))
    UnitValue.of[Percentage]("100%") shouldBe Success(Percentage(100))

    UnitValue.of[Percentage]("x") shouldBe a[Failure[_]]
    UnitValue.of[Percentage]("-50") shouldBe a[Failure[_]]
    UnitValue.of[Percentage]("1.5") shouldBe a[Failure[_]]
    UnitValue.of[Percentage]("100") shouldBe a[Failure[_]]
  }
}
