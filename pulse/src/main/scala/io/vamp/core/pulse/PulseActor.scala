package io.vamp.core.pulse

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import akka.actor.Props
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka._
import io.vamp.common.http.{OffsetEnvelope, OffsetRequestEnvelope, OffsetResponseEnvelope}
import io.vamp.common.json.{OffsetDateTimeSerializer, SerializationFormat}
import io.vamp.common.vitals.InfoRequest
import io.vamp.core.model.event.Aggregator.AggregatorType
import io.vamp.core.model.event._
import io.vamp.core.model.validator.EventValidator
import io.vamp.core.pulse.Percolator.{RegisterPercolator, UnregisterPercolator}
import io.vamp.core.pulse.notification._
import org.json4s.ext.EnumNameSerializer
import org.json4s.native.Serialization._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.language.postfixOps

object EventRequestEnvelope {
  val maxPerPage = 30
}

case class EventRequestEnvelope(request: EventQuery, page: Int, perPage: Int) extends OffsetRequestEnvelope[EventQuery]

case class EventResponseEnvelope(response: List[Event], total: Long, page: Int, perPage: Int) extends OffsetResponseEnvelope[Event]

object PulseActor extends ActorDescription {

  val configuration = ConfigFactory.load().getConfig("vamp.core.pulse")

  val timeout = Timeout(configuration.getInt("response-timeout").seconds)

  val elasticsearchUrl = configuration.getString("elasticsearch.url")

  val indexName = configuration.getString("elasticsearch.index.name")

  def props(args: Any*): Props = Props(classOf[PulseActor], args: _*)

  trait PulseMessage

  case class Publish(event: Event) extends PulseMessage

  case class Query(query: EventRequestEnvelope) extends PulseMessage

  case class QueryFirst(query: EventQuery) extends PulseMessage

  case class QueryAll(query: EventQuery) extends PulseMessage

}

class PulseActor extends Percolator with EventValidator with CommonReplyActor with CommonSupportForActors with PulseNotificationProvider {

  import ElasticsearchClient._
  import PulseActor._

  implicit val timeout = PulseActor.timeout

  override protected def requestType: Class[_] = classOf[PulseMessage]

  override protected def allowedRequestType(request: Any) = {
    super.allowedRequestType(request) || request.isInstanceOf[RegisterPercolator] || request.isInstanceOf[UnregisterPercolator]
  }

  override protected def errorRequest(request: Any): RequestError = UnsupportedPulseRequest(request)

  private lazy val elasticsearch = new ElasticsearchClient(elasticsearchUrl)

  private val indexTimeFormat: Map[String, String] = configuration.getConfig("elasticsearch.index.time-format").entrySet.asScala.map { entry =>
    entry.getKey -> entry.getValue.unwrapped.toString
  } toMap

  def reply(request: Any) = try {
    request match {

      case Start => start()

      case Shutdown => shutdown()

      case InfoRequest => info()

      case Publish(event) => (validateEvent andThen percolate andThen publish)(Event.expandTags(event))

      case Query(envelope) =>
        validateEventQuery(envelope.request)
        eventQuery(envelope)

      case QueryFirst(query) => (validateEventQuery andThen eventQuery)(query)

      case QueryAll(query) => (validateEventQuery andThen eventQueryAll)(query)

      case RegisterPercolator(name, tags, message) => registerPercolator(name, tags, message)

      case UnregisterPercolator(name) => unregisterPercolator(name)

      case _ => unsupported(request)
    }
  } catch {
    case e: Throwable => reportException(PulseResponseError(e))
  }

  private def start() = {}

  private def shutdown() = {}

  private def info() = Map[String, Any](
    "elasticsearch" -> offload(elasticsearch.get("/")),
    "index" -> offload(elasticsearch.get(s"/$indexName/_stats/docs"))
  )

  private def publish(event: Event) = try {
    implicit val formats = SerializationFormat(OffsetDateTimeSerializer, new EnumNameSerializer(Aggregator))
    val (indexName, typeName) = indexTypeName(event)
    log.debug(s"Pulse publish an event to index '$indexName/$typeName': ${event.tags}")
    offload(elasticsearch.index(indexName, Some(typeName), event)) match {
      case response: ElasticsearchIndexResponse => response
      case other =>
        log.error(s"Unexpected index result: ${other.toString}.")
        other
    }
  } catch {
    case e: Throwable => reportException(EventIndexError(e))
  }

  private def indexTypeName(event: Event): (String, String) = {
    val schema = event.`type`
    val format = indexTimeFormat.getOrElse(schema, indexTimeFormat.getOrElse("event", "YYYY-MM-dd"))
    val time = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(format))
    s"$indexName-$schema-$time" -> schema
  }

  private def eventQueryAll(query: EventQuery) = {
    def retrieve(page: Int, perPage: Int) = getEvents(query, page, perPage) match {
      case EventResponseEnvelope(list, t, _, _) => t -> list
      case _ => 0L -> Nil
    }

    val perPage = EventRequestEnvelope.maxPerPage
    val (total, events) = retrieve(1, perPage)
    if (total > events.size)
      (2 until (total / perPage + (if (total % perPage == 0) 0 else 1)).toInt).foldRight(events)((i, list) => list ++ retrieve(i, perPage)._2)
    else events
  }

  private def eventQuery(envelope: EventRequestEnvelope): Any = eventQuery(envelope.request, envelope.page, envelope.perPage)

  private def eventQuery(query: EventQuery): Any = eventQuery(query, 1, EventRequestEnvelope.maxPerPage)

  private def eventQuery(query: EventQuery, page: Int, perPage: Int): Any = {
    log.debug(s"Pulse query: $query")
    try {
      query.aggregator match {
        case None => getEvents(query, page, perPage)
        case Some(Aggregator(Aggregator.`count`, _)) => countEvents(query)
        case Some(Aggregator(aggregator, field)) => aggregateEvents(query, aggregator, field)
        case _ => throw new UnsupportedOperationException
      }
    } catch {
      case e: Throwable => reportException(EventQueryError(e))
    }
  }

  private def getEvents(query: EventQuery, page: Int, perPage: Int) = try {
    implicit val formats = SerializationFormat(OffsetDateTimeSerializer, new EnumNameSerializer(Aggregator))
    val (p, pp) = OffsetEnvelope.normalize(page, perPage, EventRequestEnvelope.maxPerPage)

    offload(elasticsearch.search(indexName, None, constructSearch(query, p, pp))) match {
      case ElasticsearchSearchResponse(hits) =>
        EventResponseEnvelope(hits.hits.flatMap(hit => Some(read[Event](write(hit._source)))), hits.total, p, pp)

      case other => reportException(EventQueryError(other))
    }
  } catch {
    case e: Throwable => reportException(EventQueryError(e))
  }

  private def countEvents(eventQuery: EventQuery) = try {
    offload(elasticsearch.count(indexName, None, constructQuery(eventQuery))) match {
      case ElasticsearchCountResponse(count) => LongValueAggregationResult(count)
      case other => reportException(EventQueryError(other))
    }
  } catch {
    case e: Throwable => reportException(EventQueryError(e))
    case e: Throwable => reportException(EventQueryError(e))
  }

  private def constructSearch(eventQuery: EventQuery, page: Int, perPage: Int): Map[Any, Any] = {
    constructQuery(eventQuery) +
      ("from" -> (page - 1) * perPage) +
      ("size" -> perPage) +
      ("sort" -> Map("timestamp" -> Map("order" -> "desc")))
  }

  private def constructQuery(eventQuery: EventQuery): Map[Any, Any] = {
    Map("query" ->
      Map("filtered" ->
        Map(
          "query" -> Map("match_all" -> Map()),
          "filter" -> Map("bool" ->
            Map("must" -> List(constructTagQuery(eventQuery.tags), constructTimeRange(eventQuery.timestamp)).filter(_.isDefined).map(_.get))
          )
        )
      )
    )
  }

  private def constructTagQuery(tags: Set[String]): Option[List[Map[String, Any]]] = tags.isEmpty match {
    case true => None
    case _ => Some(
      (for (tag <- tags) yield Map("term" -> Map("tags" -> tag))).toList
    )
  }

  private def constructTimeRange(timeRange: Option[TimeRange]): Option[Map[Any, Any]] = timeRange match {
    case Some(tr) =>
      val query = Map(
        "lt" -> tr.lt,
        "lte" -> tr.lte,
        "gt" -> tr.gt,
        "gte" -> tr.gte
      ).filter(_._2.isDefined).map { case (k, v) => k -> v.get }
      if (query.isEmpty) None else Some(Map("range" -> Map("timestamp" -> query)))

    case _ => None
  }

  private def aggregateEvents(eventQuery: EventQuery, aggregator: AggregatorType, field: Option[String]) = try {
    offload(elasticsearch.aggregate(indexName, None, constructAggregation(eventQuery, aggregator, field))) match {
      case ElasticsearchAggregationResponse(ElasticsearchAggregations(ElasticsearchAggregationValue(value))) => DoubleValueAggregationResult(value)
      case other => reportException(EventQueryError(other))
    }
  } catch {
    case e: Throwable => reportException(EventQueryError(e))
  }

  private def constructAggregation(eventQuery: EventQuery, aggregator: AggregatorType, field: Option[String]): Map[Any, Any] = {
    val aggregation = aggregator match {
      case Aggregator.average => "avg"
      case _ => aggregator.toString
    }

    val aggregationField = List("value", field.getOrElse("")).filter(_.nonEmpty).mkString(".")

    constructQuery(eventQuery) +
      ("size" -> 0) +
      ("aggs" -> Map("aggregation" -> Map(s"$aggregation" -> Map("field" -> aggregationField))))
  }
}

