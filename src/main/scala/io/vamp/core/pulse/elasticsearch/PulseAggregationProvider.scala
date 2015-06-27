package io.vamp.core.pulse.elasticsearch

import java.time.OffsetDateTime

import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.core.pulse.event._

import scala.concurrent.Future
import scala.reflect.ClassTag

trait PulseAggregationProvider extends ElasticsearchClientProvider {
  this: ExecutionContextProvider =>

  def count(tags: Set[String], from: Option[OffsetDateTime] = None, to: Option[OffsetDateTime] = None, includeLower: Boolean = true, includeUpper: Boolean = true, field: Option[String] = None): Future[LongValueAggregationResult] =
    aggregate[LongValueAggregationResult](tags, from, to, includeLower, includeUpper, Aggregator(Some(Aggregator.count), field))

  def max(tags: Set[String], from: Option[OffsetDateTime] = None, to: Option[OffsetDateTime] = None, includeLower: Boolean = true, includeUpper: Boolean = true, field: Option[String] = None): Future[DoubleValueAggregationResult] =
    aggregate[DoubleValueAggregationResult](tags, from, to, includeLower, includeUpper, Aggregator(Some(Aggregator.max), field))

  def min(tags: Set[String], from: Option[OffsetDateTime] = None, to: Option[OffsetDateTime] = None, includeLower: Boolean = true, includeUpper: Boolean = true, field: Option[String] = None): Future[DoubleValueAggregationResult] =
    aggregate[DoubleValueAggregationResult](tags, from, to, includeLower, includeUpper, Aggregator(Some(Aggregator.min), field))

  def average(tags: Set[String], from: Option[OffsetDateTime] = None, to: Option[OffsetDateTime] = None, includeLower: Boolean = true, includeUpper: Boolean = true, field: Option[String] = None): Future[DoubleValueAggregationResult] =
    aggregate[DoubleValueAggregationResult](tags, from, to, includeLower, includeUpper, Aggregator(Some(Aggregator.average), field))

  def aggregate[V <: AggregationResult : ClassTag](tags: Set[String], from: Option[OffsetDateTime], to: Option[OffsetDateTime], includeLower: Boolean, includeUpper: Boolean, aggregator: Aggregator)(implicit m: Manifest[V]): Future[V] =
    elasticsearchClient.query[V](EventQuery(tags, Some(TimeRange(from, to, includeLower, includeUpper)), Some(aggregator)))
}

