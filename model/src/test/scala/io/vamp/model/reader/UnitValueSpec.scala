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
    UnitValue.of[MegaByte](" 128 Mi ") shouldBe Success(MegaByte(128))
    UnitValue.of[MegaByte](" 128 mi ") shouldBe Success(MegaByte(128))
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

  "Quantity" should "parse" in {

    UnitValue.of[Quantity]("128") shouldBe Success(Quantity(128.0))
    UnitValue.of[Quantity]("-128.5") shouldBe Success(Quantity(-128.5))
    UnitValue.of[Quantity](" 1m ") shouldBe Success(Quantity(0.001))
    UnitValue.of[Quantity](" 0.1 ") shouldBe Success(Quantity(0.1))
    UnitValue.of[Quantity](".1") shouldBe Success(Quantity(0.1))
    UnitValue.of[Quantity]("-0.1 ") shouldBe Success(Quantity(-0.1))
    UnitValue.of[Quantity]("-.1 ") shouldBe Success(Quantity(-.1))
  }

  "Time" should "parse" in {
    // Test for second values
    UnitValue.of[Time]("1sec") shouldBe Success(Time(1))
    UnitValue.of[Time]("20s") shouldBe Success(Time(20))
    UnitValue.of[Time]("1second") shouldBe Success(Time(1))
    UnitValue.of[Time]("2seconds") shouldBe Success(Time(2))
    // Test for minute values
    UnitValue.of[Time]("20m") shouldBe Success(Time(20 * 60))
    UnitValue.of[Time]("1min") shouldBe Success(Time(60))
    UnitValue.of[Time]("1minute") shouldBe Success(Time(60))
    UnitValue.of[Time]("5minutes") shouldBe Success(Time(5 * 60))
    // Test for hourly values
    UnitValue.of[Time]("1h") shouldBe Success(Time(3600))
    UnitValue.of[Time]("2hrs") shouldBe Success(Time(2 * 3600))
    UnitValue.of[Time]("1hour") shouldBe Success(Time(3600))
    UnitValue.of[Time]("4hours") shouldBe Success(Time(4 * 3600))
  }
}
