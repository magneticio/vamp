package io.vamp.pulse

import java.net.URLEncoder

import io.vamp.common.http.{ RestClientException, RestClient }
import org.json4s.native.JsonMethods._
import org.json4s.{ StringInput, DefaultFormats, Formats }

import scala.concurrent.{ ExecutionContext, Future }
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

  case class ElasticsearchAggregationValue(value: Double)

}

class ElasticsearchClient(url: String)(implicit executor: ExecutionContext) {

  import ElasticsearchClient._

  def health = RestClient.get[Any](urlOf(url, "_cluster", "health"))

  def exists(index: String, `type`: String, id: String): Future[Boolean] = {
    RestClient.get[Any](urlOf(url, index, `type`, id), RestClient.jsonHeaders, logError = false) map {
      case response: Map[_, _] ⇒ Try(response.asInstanceOf[Map[String, Boolean]].getOrElse("found", false)).getOrElse(false)
      case _                   ⇒ false
    }
  }

  def get[A](index: String, `type`: String, id: String)(implicit mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] = {
    RestClient.get[A](urlOf(url, index, `type`, id), RestClient.jsonHeaders, logError = false).recover {
      case RestClientException(Some(404), body) ⇒ parse(StringInput(body), useBigDecimalForDouble = true).extract[A](formats, mf)
    }
  }

  def index[A](index: String, `type`: String, document: AnyRef)(implicit mf: scala.reflect.Manifest[A], formats: Formats): Future[A] =
    RestClient.post[A](urlOf(url, index, `type`), document)

  def index[A](index: String, `type`: String, id: String, document: AnyRef)(implicit mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] =
    RestClient.put[A](urlOf(url, index, `type`, id), document)

  def delete(index: String, `type`: String, id: String): Future[_] = {
    RestClient.delete(urlOf(url, index, `type`, id), RestClient.jsonHeaders, logError = false).recover {
      case _ ⇒ None
    }
  }

  def search[A](index: String, query: Any)(implicit mf: scala.reflect.Manifest[A], formats: Formats): Future[A] =
    RestClient.post[A](urlOf(url, index, "_search"), query)

  def search[A](index: String, `type`: String, query: Any)(implicit mf: scala.reflect.Manifest[A], formats: Formats = DefaultFormats): Future[A] =
    RestClient.post[A](urlOf(url, index, `type`, "_search"), query)

  def count(index: String, query: Any)(implicit formats: Formats = DefaultFormats): Future[ElasticsearchCountResponse] =
    RestClient.post[ElasticsearchCountResponse](urlOf(url, index, "_count"), query)

  def aggregate(index: String, query: Any)(implicit formats: Formats = DefaultFormats): Future[ElasticsearchAggregationResponse] =
    RestClient.post[ElasticsearchAggregationResponse](urlOf(url, index, "_search"), query)

  private def urlOf(url: String, paths: String*) = (url :: paths.map(path ⇒ URLEncoder.encode(path, "UTF-8")).toList) mkString "/"
}
