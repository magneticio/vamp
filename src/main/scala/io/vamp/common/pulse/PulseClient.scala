package io.vamp.common.pulse

import java.time.OffsetDateTime

import io.vamp.common.http.RestClient
import io.vamp.pulse.api.AggregatorType.AggregatorType
import io.vamp.pulse.api._
import io.vamp.pulse.util.Serializers
import org.json4s.Formats

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class PulseClient(val url: String)(implicit val formats: Formats = Serializers.formats) {

  import PulseClient._

  def sendEvent(event: Event): Future[Event] = RestClient.request[Event](s"POST $url/api/v1/events", event)

  def getEvents(tags: List[String], from: OffsetDateTime, to: OffsetDateTime): Future[List[Event]] = getEvents(tags, Some(from), Some(to))

  def getEvents(tags: List[String], from: Option[OffsetDateTime] = None, to: Option[OffsetDateTime] = None, `type`: String = ""): Future[List[Event]] =
    RestClient.request[List[Event]](s"POST $url/api/v1/events/get", constructQuery(tags, from, to, `type`, aggregator = None))
}

object PulseClient {

  def constructQuery(tags: List[String], from: Option[OffsetDateTime], to: Option[OffsetDateTime], `type`: String, aggregator: Option[Aggregator]): EventQuery = {
    var timeRange = TimeRange()
    if (from.isDefined) timeRange = timeRange.copy(from = from.get)
    if (to.isDefined) timeRange = timeRange.copy(to = to.get)

    var eventQuery = EventQuery(tags = tags, time = timeRange, `type` = `type`)

    if (aggregator.isDefined) eventQuery = eventQuery.copy(aggregator = aggregator)

    eventQuery
  }
}


case class SimpleAggregationResult(value: Double)

trait SimpleAggregation {
  this: PulseClient =>

  import PulseClient._

  protected def getAggregation(tags: List[String], from: Option[OffsetDateTime], to: Option[OffsetDateTime], `type`: String, field: String, aggregatorType: AggregatorType): Future[SimpleAggregationResult] = {
    RestClient.request[SimpleAggregationResult](s"POST $url/api/v1/events/get", constructQuery(tags = tags, `type` = `type`, from = from, to = to, aggregator = Some(Aggregator(aggregatorType, field))))
  }
}


trait Count extends SimpleAggregation {
  this: PulseClient =>

  def count(tags: List[String], from: OffsetDateTime, to: OffsetDateTime): Future[SimpleAggregationResult] = count(tags, Some(from), Some(to))

  def count(tags: List[String], from: Option[OffsetDateTime] = None, to: Option[OffsetDateTime] = None, `type`: String = "", field: String = ""): Future[SimpleAggregationResult] = getAggregation(tags, from, to, `type`, field, AggregatorType.count)
}

trait Max extends SimpleAggregation {
  this: PulseClient =>

  def max(tags: List[String], from: OffsetDateTime, to: OffsetDateTime): Future[SimpleAggregationResult] = max(tags, Some(from), Some(to))

  def max(tags: List[String], from: Option[OffsetDateTime] = None, to: Option[OffsetDateTime] = None, `type`: String = "", field: String = ""): Future[SimpleAggregationResult] = getAggregation(tags, from, to, `type`, field, AggregatorType.max)
}

trait Min extends SimpleAggregation {
  this: PulseClient =>

  def min(tags: List[String], from: OffsetDateTime, to: OffsetDateTime): Future[SimpleAggregationResult] = min(tags, Some(from), Some(to))

  def min(tags: List[String], from: Option[OffsetDateTime] = None, to: Option[OffsetDateTime] = None, `type`: String = "", field: String = ""): Future[SimpleAggregationResult] = getAggregation(tags, from, to, `type`, field, AggregatorType.min)
}

trait Average extends SimpleAggregation {
  this: PulseClient =>

  def average(tags: List[String], from: OffsetDateTime, to: OffsetDateTime): Future[SimpleAggregationResult] = average(tags, Some(from), Some(to))

  def average(tags: List[String], from: Option[OffsetDateTime] = None, to: Option[OffsetDateTime] = None, `type`: String = "", field: String = ""): Future[SimpleAggregationResult] = getAggregation(tags, from, to, `type`, field, AggregatorType.average)
}



