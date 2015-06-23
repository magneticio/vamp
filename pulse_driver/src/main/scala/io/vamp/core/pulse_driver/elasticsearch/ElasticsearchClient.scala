package io.vamp.core.pulse_driver.elasticsearch

import java.time.OffsetDateTime

import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.common.http.RestClient
import io.vamp.common.json.OffsetDateTimeSerializer
import io.vamp.core.pulse_driver.model.{Aggregator, Event, EventQuery, TimeRange}
import org.json4s.DefaultFormats
import org.json4s.ext.EnumNameSerializer

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag


class ElasticsearchClient(val url: String)(implicit executionContext: ExecutionContext) {

  def info: Future[Any] = RestClient.request[Any](s"GET $url/api/v1/info")

  def sendEvent(event: Event): Future[Event] = RestClient.request[Event](s"POST $url/api/v1/events", event)

  def getEvents(tags: Set[String], from: Option[OffsetDateTime] = None, to: Option[OffsetDateTime] = None, includeLower: Boolean = true, includeUpper: Boolean = true): Future[List[Event]] = {
    query[List[Event]](EventQuery(tags = tags, Some(TimeRange.from(from, to, includeLower, includeUpper))))
  }

  def query[T <: Any : ClassTag](query: EventQuery)(implicit mf: scala.reflect.Manifest[T]): Future[T] = {
    implicit val formats = DefaultFormats + new OffsetDateTimeSerializer() + new EnumNameSerializer(Aggregator)
    RestClient.request[T](s"POST $url/api/v1/events/get", query)
  }
}

trait ElasticsearchClientProvider {
  this: ExecutionContextProvider =>

  protected def elasticsearchUrl: String

  lazy val elasticsearchClient = new ElasticsearchClient(elasticsearchUrl)
}
