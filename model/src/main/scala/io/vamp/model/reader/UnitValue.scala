package io.vamp.model.reader

import scala.reflect._
import scala.util.{ Failure, Try }

sealed trait UnitValue[T] {
  def value: T

  def normalized: String

  override def toString = normalized
}

object UnitValue {

  def of[V <: Any: ClassTag](value: Any): Try[V] = value match {
    case _ if classTag[V].runtimeClass == classOf[Percentage] ⇒ Try(Percentage.of(value).asInstanceOf[V])
    case _ if classTag[V].runtimeClass == classOf[MegaByte] ⇒ Try(MegaByte.of(value).asInstanceOf[V])
    case _ if classTag[V].runtimeClass == classOf[Quantity] ⇒ Try(Quantity.of(value).asInstanceOf[V])
    case _ if classTag[V].runtimeClass == classOf[Time]     => Try(Time.of(value).asInstanceOf[V])
    case _ ⇒ Failure(new IllegalArgumentException())
  }

  def illegal(value: Any) = throw new IllegalArgumentException(s"illegal value: $value")
}

object Percentage {

  private val percentagePattern = """^\s*(\d{1,3})\s*%\s*$""".r

  def of(source: Any): Percentage = source match {
    case string: String ⇒ string match {
      case percentagePattern(p) ⇒ Percentage(p.toInt)
      case _                    ⇒ throw new IllegalArgumentException()
    }
    case _ ⇒ UnitValue.illegal(source)
  }
}

case class Percentage(value: Int) extends UnitValue[Int] {
  {
    if (value < 0 || value > 100) UnitValue.illegal(value)
  }

  def normalized = s"$value%"
}

object MegaByte {

  private val megaBytePattern1 = """^\s*(.\d+)\s*[M|m]\s*[B|b]{0,1}\s*$""".r
  private val megaBytePattern2 = """^\s*(\d+.*\d*)\s*[M|m]\s*[B|b]{0,1}\s*$""".r
  private val megaBytePattern3 = """^\s*(\d+.*\d*)\s*[M|m]\s*[I|i]{0,1}\s*$""".r
  private val gigaBytePattern1 = """^\s*(.\d+)\s*[G|g]\s*[B|b]{0,1}\s*$""".r
  private val gigaBytePattern2 = """^\s*(\d+.*\d*)\s*[G|g]\s*[B|b]{0,1}\s*$""".r

  def of(source: Any): MegaByte = source match {
    case string: String ⇒ string match {
      case megaBytePattern1(mb) ⇒ MegaByte(mb.toDouble)
      case megaBytePattern2(mb) ⇒ MegaByte(mb.toDouble)
      case megaBytePattern3(mb) ⇒ MegaByte(mb.toDouble)
      case gigaBytePattern1(gb) ⇒ MegaByte(MegaByte.gigaByte2MegaByte(gb.toDouble))
      case gigaBytePattern2(gb) ⇒ MegaByte(MegaByte.gigaByte2MegaByte(gb.toDouble))
      case _                    ⇒ throw new IllegalArgumentException()
    }
    case _ ⇒ UnitValue.illegal(source)
  }

  def gigaByte2MegaByte(gb: Double): Double = 1024 * gb
}

case class MegaByte(value: Double) extends UnitValue[Double] {
  {
    if (value < 0) UnitValue.illegal(value)
  }

  def normalized = f"$value%.2fMB"
}

object Quantity {

  private val pattern = """^\s*(.+)\s*$""".r
  private val milliPattern = """^\s*(.+?)\s*m\s*$""".r

  def of(source: Any): Quantity = source match {
    case string: String ⇒ string match {
      case milliPattern(m) ⇒ Quantity(m.toDouble / 1000)
      case pattern(m)      ⇒ Quantity(m.toDouble)
      case _               ⇒ throw new IllegalArgumentException()
    }
    case _ ⇒ Try(Quantity(source.toString.toDouble)).getOrElse(UnitValue.illegal(source))
  }
}

case class Quantity(value: Double) extends UnitValue[Double] {
  def normalized = f"$value%.2f"
}

object Time {

  private val secondPattern = "(\\d+)(s|sec|second|seconds)".r
  private val minutePattern = "(\\d+)(m|min|minute|minutes)".r
  private val hourPattern   = "(\\d+)(h|hrs|hour|hours)".r

  def of(source: Any): Time = source match {
    case string: String         => string match {
      case secondPattern(s, _)  => Time(s.toInt)
      case minutePattern(m, _)  => Time(m.toInt * 60)
      case hourPattern(h, _)    => Time(h.toInt * 3600)
      case s                    => throw new IllegalArgumentException(s)
    }
    case _ ⇒ Try(Time(source.toString.toInt)).getOrElse(UnitValue.illegal(source))
  }
}

/**
 * Time defines a UnitValue for minutes (m) and seconds (s)
 */
case class Time(value: Int) extends UnitValue[Int] {
  override def normalized = s"${value}s"
}