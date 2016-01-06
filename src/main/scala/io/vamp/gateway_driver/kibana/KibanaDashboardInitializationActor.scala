package io.vamp.gateway_driver.kibana

import io.vamp.common.http.RestClient
import io.vamp.gateway_driver.logstash.Logstash
import io.vamp.gateway_driver.notification.GatewayDriverNotificationProvider
import io.vamp.pulse.ElasticsearchClient
import io.vamp.pulse.ElasticsearchClient.ElasticsearchSearchResponse
import io.vamp.pulse.elasticsearch.ElasticsearchInitializationActor
import io.vamp.pulse.elasticsearch.ElasticsearchInitializationActor.{ DocumentDefinition, DoneWithOne, WaitForOne }

import scala.io.Source

class KibanaDashboardInitializationActor extends ElasticsearchInitializationActor with GatewayDriverNotificationProvider {

  import KibanaDashboardActor._

  lazy val elasticsearchUrl = KibanaDashboardActor.elasticsearchUrl

  override lazy val documents: List[DocumentDefinition] = {
    def load(name: String) = Source.fromInputStream(getClass.getResourceAsStream(s"$name.json")).mkString
    if (enabled) List(DocumentDefinition(kibanaIndex, "index-pattern", Logstash.index, load("kibana_init"))) else Nil
  }

  override protected def initializeCustom(): Unit = {
    val receiver = self

    if (enabled) RestClient.post[ElasticsearchSearchResponse](s"$elasticsearchUrl/$kibanaIndex/config/_search", Map()) map {
      case response: ElasticsearchSearchResponse ⇒
        response.hits.hits.foreach { hit ⇒
          hit._source.get("defaultIndex") match {
            case None ⇒
              val es = new ElasticsearchClient(elasticsearchUrl)
              log.info(s"Setting default Kibana search index for '${hit._id}' to '${Logstash.index}'.")

              receiver ! WaitForOne
              es.index[Any](hit._index, hit._type, hit._id, hit._source + ("defaultIndex" -> Logstash.index)) onComplete {
                case _ ⇒ receiver ! DoneWithOne
              }

              receiver ! WaitForOne
              es.index[Any](kibanaIndex, "index-pattern", Logstash.index, index) onComplete {
                case _ ⇒ receiver ! DoneWithOne
              }

            case _ ⇒
          }
        }
      case other ⇒ log.error(s"Kibana usage is enabled but no Kibana index '$kibanaIndex' found: ${other.toString}")
    }

    receiver ! DoneWithOne
  }

  private def index =
    s"""
      |{
      |  "title": "${Logstash.index}",
      |  "timeFieldName": "@timestamp",
      |  "fields": "[{\\\"name\\\":\\\"_index\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":false,\\\"analyzed\\\":false,\\\"doc_values\\\":false},{\\\"name\\\":\\\"geoip.ip\\\",\\\"type\\\":\\\"ip\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"@timestamp\\\",\\\"type\\\":\\\"date\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"geoip.location\\\",\\\"type\\\":\\\"geo_point\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":false},{\\\"name\\\":\\\"@version\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"geoip.latitude\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"_source\\\",\\\"type\\\":\\\"_source\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":false,\\\"analyzed\\\":false,\\\"doc_values\\\":false},{\\\"name\\\":\\\"geoip.longitude\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"CC.raw\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"hr\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":true,\\\"doc_values\\\":false},{\\\"name\\\":\\\"hs\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":true,\\\"doc_values\\\":false},{\\\"name\\\":\\\"type\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":true,\\\"doc_values\\\":false},{\\\"name\\\":\\\"ft.raw\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"Tc\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"hr.raw\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"metrics.raw\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"host\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":true,\\\"doc_values\\\":false},{\\\"name\\\":\\\"Tq\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"Tr\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"Tt\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"ac\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"Tw\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"hs.raw\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"rc\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"metrics\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":true,\\\"doc_values\\\":false},{\\\"name\\\":\\\"fc\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"bc\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"B\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"tsc\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":true,\\\"doc_values\\\":false},{\\\"name\\\":\\\"s.raw\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"ft\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":true,\\\"doc_values\\\":false},{\\\"name\\\":\\\"bq\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"sc\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"CS.raw\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"t.raw\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"sq\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"CC\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":true,\\\"doc_values\\\":false},{\\\"name\\\":\\\"ST\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"b\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":true,\\\"doc_values\\\":false},{\\\"name\\\":\\\"ci\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":true,\\\"doc_values\\\":false},{\\\"name\\\":\\\"host.raw\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"b.raw\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"tsc.raw\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"type.raw\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"message\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":true,\\\"doc_values\\\":false},{\\\"name\\\":\\\"cp\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"ci.raw\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"CS\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":true,\\\"doc_values\\\":false},{\\\"name\\\":\\\"r.raw\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":false,\\\"doc_values\\\":true},{\\\"name\\\":\\\"r\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":true,\\\"doc_values\\\":false},{\\\"name\\\":\\\"s\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":true,\\\"doc_values\\\":false},{\\\"name\\\":\\\"t\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":true,\\\"analyzed\\\":true,\\\"doc_values\\\":false},{\\\"name\\\":\\\"_id\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":false,\\\"analyzed\\\":false,\\\"doc_values\\\":false},{\\\"name\\\":\\\"_type\\\",\\\"type\\\":\\\"string\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":false,\\\"analyzed\\\":false,\\\"doc_values\\\":false},{\\\"name\\\":\\\"_score\\\",\\\"type\\\":\\\"number\\\",\\\"count\\\":0,\\\"scripted\\\":false,\\\"indexed\\\":false,\\\"analyzed\\\":false,\\\"doc_values\\\":false}]"
      |}
    """.stripMargin
}

