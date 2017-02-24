package io.vamp.pulse

import java.net.URLEncoder
import java.time.format.DateTimeFormatter._
import java.time.{ Instant, ZoneId, ZonedDateTime }

import akka.actor.ActorSystem
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.http.{ HttpClient, HttpClientException }
import org.json4s.native.JsonMethods._
import org.json4s.{ DefaultFormats, Formats, StringInput }

import scala.concurrent.Future
import scala.util.Try

object ElasticsearchClient {

  case class ElasticsearchIndexResponse(_index: String, _type: String, _id: String)

  case class ElasticsearchSearchResponse(hits: ElasticsearchSearchHits)

  case class ElasticsearchSearchHits(total: Long, hits: List[ElasticsearchHit])

  case class ElasticsearchHit(_index: String, _type: String, _id: String, _source: Map[String, Any] = Map())

  case class ElasticsearchGetResponse(_index: String, _type: String, _id: String, found: Boolean, _source: Map[String, Any] = Map())

  case class ElasticsearchCountResponse(count: Long)

  case class ElasticsearchAggregationResponse(aggregations: ElasticsearchAggregations)

  case class ElasticsearchAggregations(aggregation: ElasticsearchAggregationValue)

  case class ElasticsearchAggregationValue(value: Double = 0)

}

class ElasticsearchClient(url: String)(implicit val timeout: Timeout, val namespace: Namespace, val system: ActorSystem) {

  import ElasticsearchClient._

  private val httpClient = new HttpClient

  implicit val executionContext = system.dispatcher

  def health = httpClient.get[Any](urlOf(url, "_cluster", "health"))

  def creationTime(index: String): Future[String] = httpClient.get[Any](urlOf(url, index)) map {
    case response: Map[_, _] ⇒ Try {
      response.asInstanceOf[Map[String, _]].get(index).flatMap {
        _.asInstanceOf[Map[String, _]].get("settings")
      } flatMap {
        _.asInstanceOf[Map[String, _]].get("index")
      } flatMap {
        _.asInstanceOf[Map[String, _]].get("creation_date")
      } map {
        timestamp ⇒ ISO_OFFSET_DATE_TIME.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp.toString.toLong), ZoneId.of("UTC")))
      } getOrElse ""
    } getOrElse ""
    case _ ⇒ ""
  }

  def exists(index: String, `type`: String, id: String): Future[Boolean] = {
    httpClient.get[Any](urlOf(url, index, `type`, id), logError = false) map {
      case response: Map[_, _] ⇒ Try(response.asInstanceOf[Map[String, Boolean]].getOrElse("found", false)).getOrElse(false)
      case _                   ⇒ false
    }
  }

  def get[A](index: String, `type`: String, id: String)(implicit mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {
    httpClient.get[A](urlOf(url, index, `type`, id), logError = false).recover {
      case HttpClientException(Some(404), body) ⇒ parse(StringInput(body), useBigDecimalForDouble = true).extract[A](formats, mf)
    }
  }

  def index[A](index: String, `type`: String, document: AnyRef)(implicit mf: scala.reflect.Manifest[A], formats: Formats): Future[A] =
    httpClient.post[A](urlOf(url, index, `type`), document)

  def index[A](index: String, `type`: String, id: String, document: AnyRef)(implicit mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] =
    httpClient.post[A](urlOf(url, index, `type`, id), document)

  def delete(index: String, `type`: String, id: String): Future[_] = {
    httpClient.delete(urlOf(url, index, `type`, id), logError = false).recover {
      case _ ⇒ None
    }
  }

  def refresh(index: String): Future[_] = httpClient.post(urlOf(url, index, "_refresh"), "")

  def search[A](index: String, query: Any)(implicit mf: scala.reflect.Manifest[A], formats: Formats): Future[A] =
    httpClient.post[A](urlOf(url, index, "_search"), query)

  def search[A](index: String, `type`: String, query: Any)(implicit mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] =
    httpClient.post[A](urlOf(url, index, `type`, "_search"), query)

  def count(index: String, query: Any)(implicit formats: Formats = DefaultFormats): Future[ElasticsearchCountResponse] =
    httpClient.post[ElasticsearchCountResponse](urlOf(url, index, "_count"), query)

  def aggregate(index: String, query: Any)(implicit formats: Formats = DefaultFormats): Future[ElasticsearchAggregationResponse] =
    httpClient.post[ElasticsearchAggregationResponse](urlOf(url, index, "_search"), query)

  private def urlOf(url: String, paths: String*) = (url :: paths.map(path ⇒ URLEncoder.encode(path, "UTF-8")).toList) mkString "/"
}
