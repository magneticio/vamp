package io.vamp.gateway_driver.kibana

import akka.actor._
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.common.vitals.InfoRequest
import io.vamp.gateway_driver.haproxy.Flatten
import io.vamp.gateway_driver.kibana.KibanaDashboardActor.KibanaDashboardUpdate
import io.vamp.gateway_driver.notification.GatewayDriverNotificationProvider
import io.vamp.model.artifact.Deployment
import io.vamp.persistence.{ ArtifactPaginationSupport, PersistenceActor }
import io.vamp.pulse.ElasticsearchClient

import scala.concurrent.Future
import scala.language.postfixOps

class KibanaDashboardSchedulerActor extends SchedulerActor with GatewayDriverNotificationProvider {

  def tick() = IoC.actorFor[KibanaDashboardActor] ! KibanaDashboardUpdate
}

object KibanaDashboardActor {

  object KibanaDashboardUpdate

  val kibanaIndex = ".kibana"

  val logstashType = "haproxy"

  val configuration = ConfigFactory.load().getConfig("vamp.gateway-driver.kibana")

  val enabled = configuration.getBoolean("enabled")

  val logstashIndex = configuration.getString("logstash.index")

  val elasticsearchUrl = configuration.getString("elasticsearch.url")
}

class KibanaDashboardActor extends ArtifactPaginationSupport with CommonSupportForActors with GatewayDriverNotificationProvider {

  import KibanaDashboardActor._

  private val es = new ElasticsearchClient(elasticsearchUrl)

  def receive: Receive = {
    case InfoRequest ⇒ reply(info)

    case KibanaDashboardUpdate ⇒ if (enabled) {
      implicit val timeout = PersistenceActor.timeout
      allArtifacts[Deployment] map update
    }

    case _ ⇒
  }

  private def info = Future.successful {
    Map("enabled" -> enabled, "logstash-index" -> logstashIndex)
  }

  private def update(deployments: List[Deployment]): Unit = deployments.foreach { deployment ⇒
    deployment.clusters.foreach { cluster ⇒
      cluster.services.filter(_.state.isDeployed).foreach { service ⇒
        service.breed.ports.foreach { port ⇒
          val id = Flatten.flatten(s"${deployment.name}:${cluster.name}:${port.number}::${service.breed.name}")

          update("search", id, searchDocument)
          update("visualization", s"tt_$id", ttVisualizationDocument(id))
          update("visualization", s"count_$id", countVisualizationDocument(id))
        }
      }
    }
  }

  private def update(`type`: String, id: String, create: (String) ⇒ AnyRef): Unit = {
    es.exists(kibanaIndex, Option(`type`), id, () ⇒ {
      log.debug(s"Kibana ${`type`} exists for: $id")
    }, () ⇒ {
      log.info(s"Creating Kibana ${`type`} for: $id")
      es.index(kibanaIndex, `type`, Option(id), create(id))
    })
  }

  private def searchDocument(id: String) =
    s"""
       |{
       |  "title": "$id",
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
       |    "searchSourceJSON": "{\\\"index\\\":\\\"$logstashIndex\\\",\\\"highlight\\\":{\\\"pre_tags\\\":[\\\"@kibana-highlighted-field@\\\"],\\\"post_tags\\\":[\\\"@/kibana-highlighted-field@\\\"],\\\"fields\\\":{\\\"*\\\":{}},\\\"require_field_match\\\":false,\\\"fragment_size\\\":2147483647},\\\"filter\\\":[],\\\"query\\\":{\\\"query_string\\\":{\\\"query\\\":\\\"type: \\\\\\"$logstashType\\\\\\" AND b: \\\\\\"$id\\\\\\"\\\",\\\"analyze_wildcard\\\":true}}}"
       |  }
       |}
      """.stripMargin

  private def countVisualizationDocument(searchId: String)(id: String) =
    s"""
       |{
       |  "title": "$id",
       |  "visState": "{\\\"type\\\":\\\"line\\\",\\\"params\\\":{\\\"shareYAxis\\\":true,\\\"addTooltip\\\":true,\\\"addLegend\\\":true,\\\"showCircles\\\":true,\\\"smoothLines\\\":false,\\\"interpolate\\\":\\\"linear\\\",\\\"scale\\\":\\\"linear\\\",\\\"drawLinesBetweenPoints\\\":true,\\\"radiusRatio\\\":9,\\\"times\\\":[],\\\"addTimeMarker\\\":false,\\\"defaultYExtents\\\":false,\\\"setYExtents\\\":false,\\\"yAxis\\\":{}},\\\"aggs\\\":[{\\\"id\\\":\\\"1\\\",\\\"type\\\":\\\"count\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{}},{\\\"id\\\":\\\"2\\\",\\\"type\\\":\\\"date_histogram\\\",\\\"schema\\\":\\\"segment\\\",\\\"params\\\":{\\\"field\\\":\\\"@timestamp\\\",\\\"interval\\\":\\\"auto\\\",\\\"customInterval\\\":\\\"2h\\\",\\\"min_doc_count\\\":1,\\\"extended_bounds\\\":{}}}],\\\"listeners\\\":{}}",
       |  "description": "",
       |  "savedSearchId": "$searchId",
       |  "version": 1,
       |  "kibanaSavedObjectMeta": {
       |    "searchSourceJSON": "{\\\"filter\\\":[]}"
       |  }
       |}
   """.stripMargin

  private def ttVisualizationDocument(searchId: String)(id: String) =
    s"""
       |{
       |  "title": "$id",
       |  "visState": "{\\\"type\\\":\\\"line\\\",\\\"params\\\":{\\\"shareYAxis\\\":true,\\\"addTooltip\\\":true,\\\"addLegend\\\":true,\\\"showCircles\\\":true,\\\"smoothLines\\\":false,\\\"interpolate\\\":\\\"linear\\\",\\\"scale\\\":\\\"linear\\\",\\\"drawLinesBetweenPoints\\\":true,\\\"radiusRatio\\\":9,\\\"times\\\":[],\\\"addTimeMarker\\\":false,\\\"defaultYExtents\\\":false,\\\"setYExtents\\\":false,\\\"yAxis\\\":{}},\\\"aggs\\\":[{\\\"id\\\":\\\"1\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"Tt\\\"}},{\\\"id\\\":\\\"2\\\",\\\"type\\\":\\\"date_histogram\\\",\\\"schema\\\":\\\"segment\\\",\\\"params\\\":{\\\"field\\\":\\\"@timestamp\\\",\\\"interval\\\":\\\"auto\\\",\\\"customInterval\\\":\\\"2h\\\",\\\"min_doc_count\\\":1,\\\"extended_bounds\\\":{}}}],\\\"listeners\\\":{}}",
       |  "description": "",
       |  "savedSearchId": "$searchId",
       |  "version": 1,
       |  "kibanaSavedObjectMeta": {
       |    "searchSourceJSON": "{\\\"filter\\\":[]}"
       |   }
       |}
   """.stripMargin
}
