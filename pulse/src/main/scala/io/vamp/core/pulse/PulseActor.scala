package io.vamp.core.pulse

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import akka.actor.Props
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka._
import io.vamp.common.http.{OffsetEnvelope, OffsetRequestEnvelope, OffsetResponseEnvelope, RestClient}
import io.vamp.common.json.{OffsetDateTimeSerializer, SerializationFormat}
import io.vamp.common.vitals.InfoRequest
import io.vamp.core.pulse.event._
import io.vamp.core.pulse.notification._
import org.json4s.ext.EnumNameSerializer
import org.json4s.native.Serialization._
import org.json4s.{DefaultFormats, Formats}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

object EventRequestEnvelope {
  val max = 30
}

case class EventRequestEnvelope(request: EventQuery, page: Int, perPage: Int) extends OffsetRequestEnvelope[EventQuery]

case class EventResponseEnvelope(response: List[Event], total: Long, page: Int, perPage: Int) extends OffsetResponseEnvelope[Event]

object PulseActor extends ActorDescription {

  val configuration = ConfigFactory.load().getConfig("vamp.core.pulse")

  val timeout = Timeout(configuration.getInt("response-timeout").seconds)

  def props(args: Any*): Props = Props(classOf[PulseActor], args: _*)

  trait PulseMessage

  case class Publish(event: Event) extends PulseMessage

  case class Query(query: EventRequestEnvelope) extends PulseMessage

}

class PulseActor extends CommonReplyActor with CommonSupportForActors with PulseNotificationProvider {

  import PulseActor._

  implicit val timeout = PulseActor.timeout

  override protected def requestType: Class[_] = classOf[PulseMessage]

  override protected def errorRequest(request: Any): RequestError = UnsupportedPulseDriverRequest(request)

  private val indexName = configuration.getString("elasticsearch.index.name")

  private lazy val elasticsearch = new ElasticsearchClient(configuration.getString("elasticsearch.url"))

  private val indexTimeFormat: Map[String, String] = configuration.getConfig("elasticsearch.index.time-format").entrySet.asScala.map { entry =>
    entry.getKey -> entry.getValue.unwrapped.toString
  } toMap

  def reply(request: Any) = try {
    request match {

      case Start => start()

      case Shutdown => shutdown()

      case InfoRequest => info()

      case Publish(event) => publish(event)

      case Query(query) => eventQuery(query)

      case _ => unsupported(request)
    }
  } catch {
    case e: Throwable => exception(PulseResponseError(e))
  }

  private def start() = {}

  private def shutdown() = {}

  private def info() = offload(elasticsearch.info)

  private def publish(event: Event) = try {
    val (indexName, typeName) = indexTypeName(event)
    log.debug(s"Pulse publish to index '$indexName/$typeName': $event")
    offload(elasticsearch.index(indexName, Some(typeName), Event.expandTags(event))) match {
      case response: ElasticsearchIndexResponse => response
      case other =>
        log.error(s"Unexpected index result: ${other.toString}.")
        other
    }
  } catch {
    case e: Throwable => exception(EventIndexError(e))
  }

  private def indexTypeName(event: Event): (String, String) = {
    val schema = event.`type`
    val format = indexTimeFormat.getOrElse(schema, indexTimeFormat.getOrElse("event", "YYYY-MM-dd"))
    val time = OffsetDateTime.now().format(DateTimeFormatter.ofPattern(format))
    s"$indexName-$schema-$time" -> schema
  }

  private def eventQuery(envelope: EventRequestEnvelope) = {
    log.debug(s"Pulse query: $envelope")
    val eventQuery = envelope.request

    eventQuery.timestamp.foreach { time =>
      if ((time.lt.isDefined && time.lte.isDefined) || (time.gt.isDefined && time.gte.isDefined)) error(EventQueryTimeError)
    }

    try {
      eventQuery.aggregator match {
        case None => getEvents(envelope)
        case Some(Aggregator(Some(Aggregator.`count`), _)) => countEvents(eventQuery)
        // TODO case Some(aggregator) => aggregateEvents(eventQuery)
        case _ =>
      }
    } catch {
      case e: Throwable => exception(EventQueryError(e))
    }
  }

  private def getEvents(envelope: EventRequestEnvelope) = try {
    implicit val formats = SerializationFormat(OffsetDateTimeSerializer, new EnumNameSerializer(Aggregator))
    val (page, perPage) = OffsetEnvelope.normalize(envelope.page, envelope.perPage, EventRequestEnvelope.max)

    offload(elasticsearch.search(indexName, None, constructSearch(envelope.request, page, perPage))) match {
      case ElasticsearchSearchResponse(hits) =>
        EventResponseEnvelope(hits.hits.flatMap(hit => Some(read[Event](write(hit._source)))), hits.total, page, perPage)

      case other => exception(EventQueryError(other))
    }
  } catch {
    case e: Throwable => exception(EventQueryError(e))
  }

  private def countEvents(eventQuery: EventQuery) = try {
    offload(elasticsearch.count(indexName, None, constructQuery(eventQuery))) match {
      case ElasticsearchCountResponse(count) => LongValueAggregationResult(count)
      case other => exception(EventQueryError(other))
    }
  } catch {
    case e: Throwable => exception(EventQueryError(e))
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

  private def constructTagQuery(tags: Set[String]): Option[Map[Any, Any]] = tags.isEmpty match {
    case true => None
    case _ => Some(Map("term" ->
      Map("tags" -> tags.toList)
    ))
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
}

case class ElasticsearchIndexResponse(_index: String, _type: String, _id: String)

case class ElasticsearchSearchResponse(hits: ElasticsearchSearchHits)

case class ElasticsearchSearchHits(total: Long, hits: List[ElasticsearchSearchHit])

case class ElasticsearchSearchHit(_source: Map[String, Any])

case class ElasticsearchCountResponse(count: Long)

class ElasticsearchClient(url: String)(implicit executor: ExecutionContext) {

  def info: Future[Any] = RestClient.get[Any](s"$url/api/v1/info")

  def index(index: String, `type`: Option[String], document: AnyRef)(implicit formats: Formats = DefaultFormats): Future[ElasticsearchIndexResponse] =
    RestClient.post[ElasticsearchIndexResponse](s"$url/${indexType(index, `type`)}", document)

  def search(index: String, `type`: Option[String], query: Any)(implicit formats: Formats = DefaultFormats): Future[ElasticsearchSearchResponse] =
    RestClient.post[ElasticsearchSearchResponse](s"$url/${indexType(index, `type`)}/_search", query)

  def count(index: String, `type`: Option[String], query: Any)(implicit formats: Formats = DefaultFormats): Future[ElasticsearchCountResponse] =
    RestClient.post[ElasticsearchCountResponse](s"$url/${indexType(index, `type`)}/_count", query)

  private def indexType(index: String, `type`: Option[String]) = if (`type`.isDefined) s"$index/${`type`.get}" else index
}
