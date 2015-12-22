package io.vamp.gateway_driver.kibana

import java.net.URLEncoder

import akka.actor._
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.common.vitals.InfoRequest
import io.vamp.gateway_driver.GatewayMarshaller
import io.vamp.gateway_driver.haproxy.Flatten
import io.vamp.gateway_driver.kibana.KibanaDashboardActor.KibanaDashboardUpdate
import io.vamp.gateway_driver.logstash.Logstash
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

  val configuration = ConfigFactory.load().getConfig("vamp.gateway-driver.kibana")

  val enabled = configuration.getBoolean("enabled")

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

  private def info: Future[_] = Future.successful {
    Map("enabled" -> enabled, "logstash-index" -> Logstash.index)
  }

  private def update(deployments: List[Deployment]): Unit = deployments.foreach { deployment ⇒
    val result: List[Future[(String, Boolean)]] = deployment.clusters.flatMap { cluster ⇒

      cluster.services.filter(_.state.isDeployed).flatMap { service ⇒
        service.breed.ports.map { port ⇒
          val id = GatewayMarshaller.name(deployment, cluster, service, port)
          val changed = for {
            s ← update("search", id, searchDocument)
            tt ← update("visualization", s"${id}_tt", ttVisualizationDocument(id))
            count ← update("visualization", s"${id}_count", countVisualizationDocument(id))
          } yield !(s && tt && count)

          changed map {
            case c ⇒ (id, c)
          }
        }
      }
    }

    Future.sequence(result) map {
      case list if list.exists(_._2) ⇒

        val panels = list.zipWithIndex.map({
          case ((id, _), index) ⇒ panel(s"${id}_count", s"${id}_tt", 3 * index + 1)
        }).reduce((p1, p2) ⇒ s"$p1,$p2")

        update("dashboard", Flatten.flatten(s"${deployment.name}"), dashboard(panels))
    }
  }

  private def update(`type`: String, id: String, create: (String) ⇒ AnyRef): Future[Boolean] = {
    val encodedId = URLEncoder.encode(id, "UTF-8")

    es.exists(kibanaIndex, Option(`type`), encodedId, () ⇒ {
      log.debug(s"Kibana ${`type`} exists for: $id")
    }, () ⇒ {
      log.info(s"Creating Kibana ${`type`} for: $id")
      es.index(kibanaIndex, `type`, Option(encodedId), create(id))
    }) map {
      case true ⇒ true
      case _    ⇒ false
    } recover { case _ ⇒ false }
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
       |    "searchSourceJSON": "{\\\"index\\":\\\"${Logstash.index}\\\",\\\"highlight\\\":{\\\"pre_tags\\\":[\\\"@kibana-highlighted-field@\\\"],\\\"post_tags\\\":[\\\"@/kibana-highlighted-field@\\\"],\\\"fields\\\":{\\\"*\\\":{}},\\\"require_field_match\\\":false,\\\"fragment_size\\\":2147483647},\\\"filter\\\":[],\\\"query\\\":{\\\"query_string\\\":{\\\"query\\\":\\\"type: \\\\\\"${Logstash.`type`}\\\\\\" AND b: \\\\\\"$id\\\\\\"\\\",\\\"analyze_wildcard\\\":true}}}"
       |  }
       |}
      """.stripMargin

  private def countVisualizationDocument(searchId: String)(id: String) =
    s"""
       |{
       |  "title": "$id",
       |  "visState": "{\\\"type\\\":\\\"histogram\\\",\\\"params\\\":{\\\"shareYAxis\\\":true,\\\"addTooltip\\\":true,\\\"addLegend\\\":true,\\\"scale\\\":\\\"linear\\\",\\\"mode\\\":\\\"stacked\\\",\\\"times\\\":[],\\\"addTimeMarker\\\":false,\\\"defaultYExtents\\\":false,\\\"setYExtents\\\":false,\\\"yAxis\\\":{}},\\\"aggs\\\":[{\\\"id\\\":\\\"1\\\",\\\"type\\\":\\\"count\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{}},{\\\"id\\\":\\\"2\\\",\\\"type\\\":\\\"date_histogram\\\",\\\"schema\\\":\\\"segment\\\",\\\"params\\\":{\\\"field\\\":\\\"@timestamp\\\",\\\"interval\\\":\\\"auto\\\",\\\"customInterval\\\":\\\"2h\\\",\\\"min_doc_count\\\":1,\\\"extended_bounds\\\":{}}}],\\\"listeners\\\":{}}",
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
       |  "visState": "{\\\"type\\\":\\\"histogram\\\",\\\"params\\\":{\\\"shareYAxis\\\":true,\\\"addTooltip\\\":true,\\\"addLegend\\\":true,\\\"scale\\\":\\\"linear\\\",\\\"mode\\\":\\\"stacked\\\",\\\"times\\\":[],\\\"addTimeMarker\\\":false,\\\"defaultYExtents\\\":false,\\\"setYExtents\\\":false,\\\"yAxis\\\":{}},\\\"aggs\\\":[{\\\"id\\\":\\\"1\\\",\\\"type\\\":\\\"avg\\\",\\\"schema\\\":\\\"metric\\\",\\\"params\\\":{\\\"field\\\":\\\"Tt\\\"}},{\\\"id\\\":\\\"2\\\",\\\"type\\\":\\\"date_histogram\\\",\\\"schema\\\":\\\"segment\\\",\\\"params\\\":{\\\"field\\\":\\\"@timestamp\\\",\\\"interval\\\":\\\"auto\\\",\\\"customInterval\\\":\\\"2h\\\",\\\"min_doc_count\\\":1,\\\"extended_bounds\\\":{}}}],\\\"listeners\\\":{}}",
       |  "description": "",
       |  "savedSearchId": "$searchId",
       |  "version": 1,
       |  "kibanaSavedObjectMeta": {
       |    "searchSourceJSON": "{\\\"filter\\\":[]}"
       |   }
       |}
   """.stripMargin

  private def dashboard(panel: String)(id: String) =
    s"""
       |{
       |  "title": "$id",
       |  "hits": 0,
       |  "description": "",
       |  "panelsJSON": "[$panel]",
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
