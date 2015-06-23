package io.vamp.core.pulse_driver.model

import io.vamp.core.pulse_driver.model.Aggregator.AggregatorType

import scala.language.implicitConversions

object Aggregator extends Enumeration {
  type AggregatorType = Aggregator.Value

  val min, max, average, count = Value
}

case class Aggregator(`type`: Option[AggregatorType], field: Option[String] = None)


trait AggregationResult

trait SingleValueAggregationResult[T <: Any] extends AggregationResult {
  def value: T
}

case class LongValueAggregationResult(value: Long) extends SingleValueAggregationResult[Long]

case class DoubleValueAggregationResult(value: Double) extends SingleValueAggregationResult[Double]
