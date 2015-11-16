package io.vamp.pulse

import io.vamp.common.http.RestClient
import org.json4s.{ DefaultFormats, Formats }

import scala.concurrent.{ ExecutionContext, Future }

object ElasticsearchClient {

  case class ElasticsearchIndexResponse(_index: String, _type: String, _id: String)

  case class ElasticsearchSearchResponse(hits: ElasticsearchSearchHits)

  case class ElasticsearchSearchHits(total: Long, hits: List[ElasticsearchSearchHit])

  case class ElasticsearchSearchHit(_index: String, _type: String, _id: String, _source: Map[String, Any])

  case class ElasticsearchCountResponse(count: Long)

  case class ElasticsearchAggregationResponse(aggregations: ElasticsearchAggregations)

  case class ElasticsearchAggregations(aggregation: ElasticsearchAggregationValue)

  case class ElasticsearchAggregationValue(value: Double)

}

class ElasticsearchClient(url: String)(implicit executor: ExecutionContext) {

  import ElasticsearchClient._

  def get(path: String): Future[Any] = RestClient.get[Any](s"$url/$path")

  def index(index: String, `type`: String, id: Option[String], document: AnyRef)(implicit formats: Formats = DefaultFormats): Future[ElasticsearchIndexResponse] = id match {
    case None      ⇒ RestClient.post[ElasticsearchIndexResponse](s"$url/$index/${`type`}", document)
    case Some(_id) ⇒ RestClient.put[ElasticsearchIndexResponse](s"$url/$index/${`type`}/${_id}", document)
  }

  def search(index: String, `type`: Option[String], query: Any)(implicit formats: Formats = DefaultFormats): Future[ElasticsearchSearchResponse] =
    RestClient.post[ElasticsearchSearchResponse](s"$url/${indexType(index, `type`)}/_search", query)

  def count(index: String, `type`: Option[String], query: Any)(implicit formats: Formats = DefaultFormats): Future[ElasticsearchCountResponse] =
    RestClient.post[ElasticsearchCountResponse](s"$url/${indexType(index, `type`)}/_count", query)

  def aggregate(index: String, `type`: Option[String], query: Any)(implicit formats: Formats = DefaultFormats): Future[ElasticsearchAggregationResponse] =
    RestClient.post[ElasticsearchAggregationResponse](s"$url/${indexType(index, `type`)}/_search", query)

  def exists(index: String, `type`: Option[String], id: String, exists: () ⇒ Unit, notExists: () ⇒ Unit): Future[Boolean] = {
    RestClient.get[Any](s"$url/${indexType(index, `type`)}/$id", RestClient.jsonHeaders, logError = false) recover { case _ ⇒ false } map {
      case map: Map[_, _] if map.asInstanceOf[Map[String, Any]].getOrElse("found", false) == true ⇒
        exists()
        true
      case _ ⇒
        notExists()
        false
    }
  }

  private def indexType(index: String, `type`: Option[String]) = if (`type`.isDefined) s"$index/${`type`.get}" else index
}
