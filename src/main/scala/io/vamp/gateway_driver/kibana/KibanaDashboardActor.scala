package io.vamp.gateway_driver.kibana

import akka.actor._
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.common.vitals.InfoRequest
import io.vamp.gateway_driver.GatewayMarshaller
import io.vamp.gateway_driver.kibana.KibanaDashboardActor.KibanaUpdate
import io.vamp.gateway_driver.logstash.Logstash
import io.vamp.gateway_driver.notification.GatewayDriverNotificationProvider
import io.vamp.model.artifact.{ Deployment, Gateway, GatewayPath }
import io.vamp.persistence.db.{ ArtifactPaginationSupport, PersistenceActor }
import io.vamp.pulse.ElasticsearchClient
import io.vamp.pulse.ElasticsearchClient.ElasticsearchGetResponse

import scala.concurrent.Future
import scala.language.postfixOps

class KibanaDashboardSchedulerActor extends SchedulerActor with GatewayDriverNotificationProvider {

  def tick() = IoC.actorFor[KibanaDashboardActor] ! KibanaUpdate
}

object KibanaDashboardActor {

  object KibanaUpdate

  val kibanaIndex = ".kibana"

  val configuration = ConfigFactory.load().getConfig("vamp.gateway-driver.kibana")

  val enabled = configuration.getBoolean("enabled")

  val elasticsearchUrl = configuration.getString("elasticsearch.url")
}

class KibanaDashboardActor extends ArtifactPaginationSupport with CommonSupportForActors with GatewayDriverNotificationProvider {

  import KibanaDashboardActor._

  private val es = new ElasticsearchClient(elasticsearchUrl)

  def receive: Receive = {
    case InfoRequest ⇒ reply(info)

    case KibanaUpdate ⇒ if (enabled) {
      implicit val timeout = PersistenceActor.timeout
      allArtifacts[Gateway] map {
        case gateways ⇒
          allArtifacts[Deployment] map {
            case deployments ⇒ process(gateways, deployments)
          }
          process(gateways)
      }
    }

    case _ ⇒
  }

  private def info: Future[_] = Future.successful {
    Map("enabled" -> enabled, "logstash-index" -> Logstash.index)
  }

  private def process(gateways: List[Gateway]): Unit = gateways.foreach { gateway ⇒
    val name = GatewayMarshaller.name(gateway)
    val lookup = GatewayMarshaller.lookup(gateway)
    update("search", lookup, () ⇒ searchDocument(s"gateway: $name", lookup))
    update("visualization", s"${lookup}_tt", () ⇒ totalTimeVisualizationDocument(s"total time: $name", lookup))
    update("visualization", s"${lookup}_count", () ⇒ requestCountVisualizationDocument(s"request count: $name", lookup))
  }

  private def process(gateways: List[Gateway], deployments: List[Deployment]): Unit = deployments.foreach { deployment ⇒

    val deploymentGateways = gateways.filter { gateway ⇒
      GatewayPath(gateway.name).segments.head == deployment.name
    } sortBy { gateway ⇒
      GatewayPath(gateway.name).segments.length
    }

    val panels = deploymentGateways.zipWithIndex.map {
      case (gateway, index) ⇒
        val lookup = GatewayMarshaller.lookup(gateway)
        panel(s"${lookup}_count", s"${lookup}_tt", 3 * index + 1)
    }.reduce((p1, p2) ⇒ s"$p1,$p2")

    es.get[ElasticsearchGetResponse](kibanaIndex, "dashboard", deployment.lookupName) map {
      case response ⇒ if (!response.found || response._source.getOrElse("panelsJSON", "") != s"[${panels.replace("\\\"", "\"")}]") {
        log.info(s"Updating Kibana dashboard: ${deployment.name}")
        es.index[Any](kibanaIndex, "dashboard", deployment.lookupName, dashboard(deployment.name, panels))
      } else log.info(s"Kibana dashboard up to date: ${deployment.name}")
    }
  }

  private def update(`type`: String, id: String, data: () ⇒ AnyRef) = es.exists(kibanaIndex, `type`, id) recover { case _ ⇒ false } map {
    case false ⇒
      log.info(s"Creating Kibana ${`type`} for: $id")
      es.index[Any](kibanaIndex, `type`, id, data())
    case true ⇒ log.debug(s"Kibana ${`type`} exists for: $id")
  }

  private def searchDocument(name: String, lookup: String) =
    s"""
       |{
       |  "title": "$name",
       |  "description": "",
       |  "hits": 0,
       |  "columns": [
       |    "_source"
       |  ],
       |  "sort": [
       |    "@timestamp",
       |    "desc"
       |  ],
       |  "version": 1,
       |  "kibanaSavedObjectMeta": {
       |    "searchSourceJSON": "{\\\"index\\":\\\"${Logstash.index}\\\",\\\"highlight\\\":{\\\"pre_tags\\\":[\\\"@kibana-highlighted-field@\\\"],\\\"post_tags\\\":[\\\"@/kibana-highlighted-field@\\\"],\\\"fields\\\":{\\\"*\\\":{}},\\\"require_field_match\\\":false,\\\"fragment_size\\\":2147483647},\\\"filter\\\":[],\\\"query\\\":{\\\"query_string\\\":{\\\"query\\\":\\\"type: \\\\\\"${Logstash.`type`}\\\\\\" AND b: \\\\\\"$lookup\\\\\\"\\\",\\\"analyze_wildcard\\\":true}}}"
       |  }
       |}
      """.stripMargin

  private def totalTimeVisualizationDocument(name: String, searchId: String) =
    s"""
       |{
       |  "title": "$name",
       |  "visState": "{\\\"type\\\":\\\"histogram\\\",\\\"params\\\":{\\\"shareYAxis\\\":true,\\\"addTooltip\\\":true,\\\"addLegend\\\":true,\\\"scale\\\":\\\"linear\\\",\\\"mode\\\":\\\"stacked\\\",\\\"times\\\":[],\\\"addTimeMarker\\\":false,\\\"defaultYExtents\\\":false,\\\"setYExtents\\\":false,\\\"yAxis\\\":{}},\\\"aggs\\\":[{\\\"id\\\":\\\"1\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"Tt\\\"}},{\\\"id\\\":\\\"2\\\",\\\"type\\\":\\\"date_histogram\\\",\\\"schema\\\":\\\"segment\\\",\\\"params\\\":{\\\"field\\\":\\\"@timestamp\\\",\\\"interval\\\":\\\"auto\\\",\\\"customInterval\\\":\\\"2h\\\",\\\"min_doc_count\\\":1,\\\"extended_bounds\\\":{}}}],\\\"listeners\\\":{}}",
       |  "description": "",
       |  "savedSearchId": "$searchId",
       |  "version": 1,
       |  "kibanaSavedObjectMeta": {
       |    "searchSourceJSON": "{\\\"filter\\\":[]}"
       |   }
       |}
   """.stripMargin

  private def requestCountVisualizationDocument(name: String, searchId: String) =
    s"""
       |{
       |  "title": "$name",
       |  "visState": "{\\\"type\\\":\\\"histogram\\\",\\\"params\\\":{\\\"shareYAxis\\\":true,\\\"addTooltip\\\":true,\\\"addLegend\\\":true,\\\"scale\\\":\\\"linear\\\",\\\"mode\\\":\\\"stacked\\\",\\\"times\\\":[],\\\"addTimeMarker\\\":false,\\\"defaultYExtents\\\":false,\\\"setYExtents\\\":false,\\\"yAxis\\\":{}},\\\"aggs\\\":[{\\\"id\\\":\\\"1\\\",\\\"type\\\":\\\"count\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{}},{\\\"id\\\":\\\"2\\\",\\\"type\\\":\\\"date_histogram\\\",\\\"schema\\\":\\\"segment\\\",\\\"params\\\":{\\\"field\\\":\\\"@timestamp\\\",\\\"interval\\\":\\\"auto\\\",\\\"customInterval\\\":\\\"2h\\\",\\\"min_doc_count\\\":1,\\\"extended_bounds\\\":{}}}],\\\"listeners\\\":{}}",
       |  "description": "",
       |  "savedSearchId": "$searchId",
       |  "version": 1,
       |  "kibanaSavedObjectMeta": {
       |    "searchSourceJSON": "{\\\"filter\\\":[]}"
       |  }
       |}
   """.stripMargin

  private def dashboard(name: String, panels: String) =
    s"""
       |{
       |  "title": "$name",
       |  "hits": 0,
       |  "description": "",
       |  "panelsJSON": "[$panels]",
       |  "optionsJSON": "{\\\"darkTheme\\\":false}",
       |  "version": 1,
       |  "timeRestore": false,
       |  "kibanaSavedObjectMeta": {
       |    "searchSourceJSON": "{\\\"filter\\\":[{\\\"query\\\":{\\\"query_string\\\":{\\\"analyze_wildcard\\\":true,\\\"query\\\":\\\"*\\\"}}}]}"
       |  }
       |}
   """.stripMargin

  private def panel(id1: String, id2: String, row: Int) =
    s"""{\\\"col\\\":1,\\\"id\\\":\\\"$id1\\\",\\\"row\\\":$row,\\\"size_x\\\":6,\\\"size_y\\\":3,\\\"type\\\":\\\"visualization\\\"},{\\\"col\\\":7,\\\"id\\\":\\\"$id2\\\",\\\"row\\\":$row,\\\"size_x\\\":6,\\\"size_y\\\":3,\\\"type\\\":\\\"visualization\\\"}"""
}
