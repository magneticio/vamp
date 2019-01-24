package io.vamp.pulse

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import akka.actor.Actor
import io.vamp.common.{ ClassMapper, Config, ConfigMagnet, Namespace }
import io.vamp.common.http.OffsetEnvelope
import io.vamp.common.json.{ OffsetDateTimeSerializer, SerializationFormat }
import io.vamp.common.util.HashUtil
import io.vamp.common.vitals.{ InfoRequest, StatsRequest }
import io.vamp.model.event.Aggregator.AggregatorType
import io.vamp.model.event._
import io.vamp.model.resolver.NamespaceValueResolver
import io.vamp.pulse.Percolator.{ GetPercolator, RegisterPercolator, UnregisterPercolator }
import io.vamp.pulse.notification._
import org.json4s.ext.EnumNameSerializer
import org.json4s.native.Serialization.{ read, write }
import org.json4s.{ DefaultFormats, Extraction, Formats }

import scala.concurrent.Future
import scala.util.Try

class ElasticsearchPulseActorMapper extends ClassMapper {
  val name = "elasticsearch"
  val clazz: Class[_] = classOf[ElasticsearchPulseActor]
}

object ElasticsearchPulseActor {

  val config: String = PulseActor.config

  val elasticsearchUrl: ConfigMagnet[String] = Config.string(s"$config.elasticsearch.url")

  val indexName: ConfigMagnet[String] = Config.string(s"$config.elasticsearch.index.name")

  def indexTimeFormat()(implicit namespace: Namespace): Map[String, String] = {
    Config.entries(s"$config.elasticsearch.index.time-format")().map { case (key, value) ⇒ key → value.toString }
  }
}

class ElasticsearchPulseActor extends ElasticsearchPulseEvent
    with NamespaceValueResolver with PulseStats with PulseActor with PulseActorSupport {

  import ElasticsearchClient._
  import PulseActor._

  lazy val url = ElasticsearchPulseActor.elasticsearchUrl()

  lazy val indexName: String = resolveWithNamespace(ElasticsearchPulseActor.indexName(), lookup = true)

  lazy val indexTimeFormat: Map[String, String] = ElasticsearchPulseActor.indexTimeFormat()

  private lazy val es = new ElasticsearchClient(url)

  private var boolFilteredKeyword: String = "bool"

  private var mustQueryKeyword: String = "must"

  /**
   * Sets filterKeyword pending on elasticsearch version for creating the correct pulse queries in constructQuery.
   */
  override def preStart(): Unit = {
    super.preStart()
    es.version().foreach {
      case Some(version) ⇒
        if (version.take(1).toInt >= 5) {
          boolFilteredKeyword = "bool"
          mustQueryKeyword = "must"
        }
        else {
          boolFilteredKeyword = "filtered"
          mustQueryKeyword = "query"
        }
      case None ⇒ log.error("Unable to retrieve ElasticSearch version defaulting to version >= 5.")
    }
  }

  def receive: Actor.Receive = {

    case InfoRequest ⇒ reply(info)

    case StatsRequest ⇒ reply(stats)

    case Publish(event, publishEventValue) ⇒ publish(publishEventValue)

    case Query(envelope) ⇒ reply((validateEventQuery andThen eventQuery(envelope.page, envelope.perPage))(envelope.request), classOf[EventQueryError])

    case GetPercolator(name) ⇒ reply(Future.successful(getPercolator(name)))

    case RegisterPercolator(name, tags, kind, message) ⇒ registerPercolator(name, tags, kind, message)

    case UnregisterPercolator(name) ⇒ unregisterPercolator(name)

    case any ⇒ unsupported(UnsupportedPulseRequest(any))
  }

  private def info = es.health map { health ⇒ Map[String, Any]("type" → "elasticsearch", "elasticsearch" → health) }

  private def publish(publishEventValue: Boolean)(event: Event): Future[Any] = {
    implicit val formats: Formats = SerializationFormat(OffsetDateTimeSerializer, new EnumNameSerializer(Aggregator))
    val (indexName, typeName) = indexTypeName(event.`type`)
    log.info(s"Pulse publish an event to index '$indexName/$typeName': ${event.tags}")

    val attachment = (publishEventValue, event.value) match {
      case (true, str: String) ⇒ Map(typeName → str)
      case (true, any)         ⇒ Map("value" → write(any)(DefaultFormats), typeName → (if (typeName == Event.defaultType) "" else any))
      case (false, _)          ⇒ Map("value" → "")
    }

    val data = Extraction.decompose(if (publishEventValue) event else event.copy(value = None)) merge Extraction.decompose(attachment)

    es.index[ElasticsearchIndexResponse](indexName, typeName, data) map {
      case r: ElasticsearchIndexResponse ⇒ event.copy(id = Option(convertId(r._id)))
      case other ⇒
        log.error(s"Unexpected index result: ${other.toString}.")
        other
    }
  }

  private def broadcast(publishEventValue: Boolean): Future[Any] ⇒ Future[Any] = _.map {
    case event: Event ⇒ percolate(publishEventValue)(event)
    case other        ⇒ other
  }

  protected def eventQuery(page: Int, perPage: Int)(query: EventQuery): Future[Any] = {
    log.debug(s"Pulse query: $query")
    query.aggregator match {
      case None                                    ⇒ getEvents(query, page, perPage)
      case Some(Aggregator(Aggregator.`count`, _)) ⇒ countEvents(query)
      case Some(Aggregator(aggregator, field))     ⇒ aggregateEvents(query, aggregator, field)
      case _                                       ⇒ throw new UnsupportedOperationException
    }
  }

  protected def getEvents(query: EventQuery, page: Int, perPage: Int): Future[Any] = {
    implicit val formats: Formats = SerializationFormat(OffsetDateTimeSerializer, new EnumNameSerializer(Aggregator))
    val (p, pp) = OffsetEnvelope.normalize(page, perPage, EventRequestEnvelope.maxPerPage)

    // TODO: testing index type name, try using * as default value
    // val (indexName, typeName) = indexTypeName(query.`type`.getOrElse(Event.defaultType))

    logger.info("Get Events called for index {} type was {}", indexName, query.`type`.getOrElse("None"))

    // '*' is added to index search so all types of events are returned.
    es.search[ElasticsearchSearchResponse](indexName + "*", constructSearch(query, p, pp)) map {
      case ElasticsearchSearchResponse(hits) ⇒
        val events = hits.hits.flatMap { hit ⇒
          Try(read[Event](write(hit._source)).copy(id = Option(convertId(hit._id)), digest = hit._source.get("digest").asInstanceOf[Option[String]])).toOption
        }
        EventResponseEnvelope(events, hits.total, p, pp)
      case other ⇒ {
        logger.info("Get Events called for index {} and an error is occurred.", indexName)
        reportException(EventQueryError(other))
      }
    }
  }

  protected def countEvents(eventQuery: EventQuery): Future[Any] = es.count(indexName, constructQuery(eventQuery)) map {
    case ElasticsearchCountResponse(count) ⇒ LongValueAggregationResult(count)
    case other                             ⇒ reportException(EventQueryError(other))
  }

  private def constructSearch(eventQuery: EventQuery, page: Int, perPage: Int): Map[Any, Any] = {
    constructQuery(eventQuery) +
      ("from" → ((page - 1) * perPage)) +
      ("size" → perPage) +
      ("sort" → Map("timestamp" → Map("order" → "desc")))
  }

  private def constructQuery(eventQuery: EventQuery): Map[Any, Any] =
    Map("query" →
      Map(boolFilteredKeyword →
        Map(
          mustQueryKeyword → Map("match_all" → Map()),
          "filter" → Map("bool" →
            Map("must" → List(
              constructTagQuery(eventQuery.tags),
              constructTypeQuery(eventQuery.`type`),
              constructTimeRange(eventQuery.timestamp)
            ).filter(_.isDefined).map(_.get)))
        )))

  private def constructTagQuery(tags: Set[String]): Option[List[Map[String, Any]]] = {
    if (tags.nonEmpty) Option((for (tag ← tags) yield Map("term" → Map("tags" → tag))).toList) else None
  }

  private def constructTypeQuery(`type`: Option[String]): Option[Map[String, Any]] = {
    if (`type`.nonEmpty) Option(Map("term" → Map("type" → `type`.get))) else None
  }

  private def constructTimeRange(timeRange: Option[TimeRange]): Option[Map[Any, Any]] = timeRange match {
    case Some(tr) ⇒
      val query = Map(
        "lt" → tr.lt,
        "lte" → tr.lte,
        "gt" → tr.gt,
        "gte" → tr.gte
      ).filter(_._2.isDefined).map { case (k, v) ⇒ k → v.get }
      if (query.isEmpty) None else Some(Map("range" → Map("timestamp" → query)))

    case _ ⇒ None
  }

  protected def aggregateEvents(eventQuery: EventQuery, aggregator: AggregatorType, field: Option[String]): Future[Any] = {
    es.aggregate(indexName, constructAggregation(eventQuery, aggregator, field)) map {
      case ElasticsearchAggregationResponse(ElasticsearchAggregations(ElasticsearchAggregationValue(value))) ⇒ DoubleValueAggregationResult(value)
      case other ⇒ reportException(EventQueryError(other))
    }
  }

  private def constructAggregation(eventQuery: EventQuery, aggregator: AggregatorType, field: Option[String]): Map[Any, Any] = {
    val aggregation = aggregator match {
      case Aggregator.average ⇒ "avg"
      case _                  ⇒ aggregator.toString
    }

    constructQuery(eventQuery) +
      ("size" → 0) +
      ("aggs" → Map("aggregation" → Map(s"$aggregation" → Map("field" → field.getOrElse("value")))))
  }

  private def convertId(id: String) = HashUtil.hex(id)
}

trait ElasticsearchPulseEvent {

  def indexName: String

  def indexTimeFormat: Map[String, String]

  def indexTypeName(schema: String = Event.defaultType, interpolateTime: Boolean = true): (String, String) = {
    val base = schema.toLowerCase
    val format = indexTimeFormat.getOrElse(base, indexTimeFormat.getOrElse(Event.defaultType, "YYYY-MM-dd"))
    val time = if (interpolateTime) OffsetDateTime.now().format(DateTimeFormatter.ofPattern(format)) else s"{$format}"
    s"$indexName-$base-$time" → base
  }
}