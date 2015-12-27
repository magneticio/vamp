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

  "MegaByte" should "parse" in {

    UnitValue.of[MegaByte]("128mb") shouldBe Success(MegaByte(128))
    UnitValue.of[MegaByte](" 128mb ") shouldBe Success(MegaByte(128))
    UnitValue.of[MegaByte](" 128 mb ") shouldBe Success(MegaByte(128))
    UnitValue.of[MegaByte](".1m") shouldBe Success(MegaByte(0.1))
    UnitValue.of[MegaByte]("10.1Mb") shouldBe Success(MegaByte(10.1))
    UnitValue.of[MegaByte]("64.MB") shouldBe Success(MegaByte(64))
    UnitValue.of[MegaByte](".1gb") shouldBe Success(MegaByte(102.4))
    UnitValue.of[MegaByte]("1GB") shouldBe Success(MegaByte(1024))
    UnitValue.of[MegaByte]("1.5G") shouldBe Success(MegaByte(1536))
    UnitValue.of[MegaByte](".1gB") shouldBe Success(MegaByte(102.4))

    UnitValue.of[MegaByte]("1") shouldBe a[Failure[_]]
    UnitValue.of[MegaByte]("-1") shouldBe a[Failure[_]]
    UnitValue.of[MegaByte]("1kb") shouldBe a[Failure[_]]
    UnitValue.of[MegaByte](".") shouldBe a[Failure[_]]
  }
}
