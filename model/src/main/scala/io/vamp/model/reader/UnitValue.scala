package io.vamp.model.reader

import scala.language.implicitConversions
import scala.reflect._
import scala.util.{ Failure, Try }

sealed trait UnitValue[T] {
  def value: T

  def normalized: String

  override def toString = normalized
}

object UnitValue {

  private val percentagePattern = """^\s*(\d{1,3})\s*%\s*$""".r

  def of[V <: Any: ClassTag](value: Any): Try[V] = value match {
    case _ if classTag[V].runtimeClass == classOf[Percentage] ⇒ Try(percentage(value).asInstanceOf[V])
    case _ ⇒ Failure(new IllegalArgumentException())
  }

  def percentage(source: Any): Percentage = source match {
    case string: String ⇒ string match {
      case percentagePattern(p) ⇒ Percentage(p.toInt)
      case _                    ⇒ throw new IllegalArgumentException()
    }
    case _ ⇒ throw new IllegalArgumentException()
  }
}

case class Percentage(value: Int) extends UnitValue[Int] {
  {
    if (value < 0 || value > 100) throw new IllegalArgumentException()
  }

  def normalized = s"$value%"
}
