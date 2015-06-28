package io.vamp.core.model.event

import io.vamp.core.model.event.Aggregator.AggregatorType


object Aggregator extends Enumeration {
  type AggregatorType = Aggregator.Value

  val min, max, average, sum, count = Value
}

case class Aggregator(`type`: AggregatorType, field: Option[String] = None)

trait AggregationResult

trait SingleValueAggregationResult[T <: Any] extends AggregationResult {
  def value: T
}

case class LongValueAggregationResult(value: Long) extends SingleValueAggregationResult[Long]

case class DoubleValueAggregationResult(value: Double) extends SingleValueAggregationResult[Double]
