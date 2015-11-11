package io.vamp.gateway_driver.kibana

import io.vamp.common.http.RestClient
import io.vamp.gateway_driver.notification.GatewayDriverNotificationProvider
import io.vamp.pulse.ElasticsearchClient.ElasticsearchSearchResponse
import io.vamp.pulse.elasticsearch.ElasticsearchInitializationActor
import io.vamp.pulse.elasticsearch.ElasticsearchInitializationActor.{ WaitForOne, DoneWithOne, DocumentDefinition }

import scala.io.Source

class KibanaDashboardInitializationActor extends ElasticsearchInitializationActor with GatewayDriverNotificationProvider {

  import KibanaDashboardActor._

  lazy val elasticsearchUrl = KibanaDashboardActor.elasticsearchUrl

  private val kibanaIndex = ".kibana"

  override lazy val documents: List[DocumentDefinition] = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"$name.json")).mkString
    if (enabled) List(DocumentDefinition(kibanaIndex, "index-pattern", logstashIndex, load("kibana_init"))) else Nil
  }

  override protected def initializeCustom(): Unit = {
    val receiver = self

    if (enabled) RestClient.post[ElasticsearchSearchResponse](s"$elasticsearchUrl/$kibanaIndex/config/_search", Map()) map {
      case response: ElasticsearchSearchResponse ⇒
        response.hits.hits.foreach { hit ⇒
          hit._source.get("defaultIndex") match {
            case None ⇒
              log.info(s"Setting default Kibana search index for '${hit._id}' to '$logstashIndex'.")
              receiver ! WaitForOne
              RestClient.put[Any](s"$elasticsearchUrl/${hit._index}/${hit._type}/${hit._id}", hit._source + ("defaultIndex" -> logstashIndex)) onComplete {
                case _ ⇒ receiver ! DoneWithOne
              }

            case _ ⇒
          }
        }
      case other ⇒ log.error(s"Kibana usage is enabled but no Kibana index '$kibanaIndex' found: ${other.toString}")
    }

    receiver ! DoneWithOne
  }
}
