package io.vamp.gateway_driver.aggregation

import java.time.OffsetDateTime

import akka.actor._
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.gateway_driver.haproxy.Flatten
import io.vamp.gateway_driver.logstash.Logstash
import io.vamp.gateway_driver.notification.GatewayDriverNotificationProvider
import io.vamp.model.artifact.Deployment
import io.vamp.model.event.Event
import io.vamp.persistence.{ ArtifactPaginationSupport, PersistenceActor }
import io.vamp.pulse.PulseActor.Publish
import io.vamp.pulse.{ ElasticsearchClient, PulseActor, PulseEvent }

import scala.language.postfixOps

class EndpointMetricsSchedulerActor extends SchedulerActor with GatewayDriverNotificationProvider {

  def tick() = IoC.actorFor[EndpointMetricsActor] ! EndpointMetricsActor.EndpointMetricsUpdate
}

object EndpointMetricsActor {

  object EndpointMetricsUpdate

}

/**
 * Workaround for current (old) UI and showing metrics.
 * This is a naive implementation for aggregation of response time and request rate of endpoints.
 * This should be removed once we have an updated UI.
 */
class EndpointMetricsActor extends PulseEvent with ArtifactPaginationSupport with CommonSupportForActors with GatewayDriverNotificationProvider {

  import EndpointMetricsActor._

  implicit val timeout = PersistenceActor.timeout

  private val es = new ElasticsearchClient(PulseActor.elasticsearchUrl)

  def receive: Receive = {
    case EndpointMetricsUpdate ⇒ allArtifacts[Deployment] map update
    case _                     ⇒
  }

  private def update(deployments: List[Deployment]) = deployments.foreach { deployment ⇒
    deployment.endpoints.foreach { endpoint ⇒
      val name = s"${deployment.name}_${endpoint.number}"

      val period = 60
      val flatten = Flatten.flatten(name)

      es.searchRaw(Logstash.index, Option(Logstash.`type`),
        s"""
           |{
           |  "query": {
           |    "filtered": {
           |      "query": {
           |        "match_all": {}
           |      },
           |      "filter": {
           |        "bool": {
           |          "must": [
           |            {
           |              "term": {
           |                "b": "$flatten"
           |              }
           |            },
           |            {
           |              "range": {
           |                "@timestamp": {
           |                  "gt": "now-${period}s"
           |                }
           |              }
           |            }
           |          ]
           |        }
           |      }
           |    }
           |  },
           |  "aggregations": {
           |    "Tt": {
           |      "avg": {
           |        "field": "Tt"
           |      }
           |    }
           |  },
           |  "size": 0
           |}
        """.stripMargin) map {
          case map: Map[_, _] ⇒

            val count: Long = map.asInstanceOf[Map[String, _]].get("hits").flatMap(map ⇒ map.asInstanceOf[Map[String, _]].get("total")) match {
              case Some(number: BigInt) ⇒ number.toLong
              case _                    ⇒ 0L
            }

            val rate: Double = count.toDouble / period

            val responseTime: Double = map.asInstanceOf[Map[String, _]].get("aggregations").flatMap(map ⇒ map.asInstanceOf[Map[String, _]].get("Tt")).flatMap(map ⇒ map.asInstanceOf[Map[String, _]].get("value")) match {
              case Some(number: BigInt)     ⇒ number.toDouble
              case Some(number: BigDecimal) ⇒ number.toDouble
              case _                        ⇒ 0D
            }

            log.debug(s"Request count/rate/responseTime for $name: $count/$rate/$responseTime")

            actorFor[PulseActor] ! Publish(Event(Set(s"endpoints:$name", "metrics:rate"), Double.box(rate), OffsetDateTime.now(), "endpoint-rate"))
            actorFor[PulseActor] ! Publish(Event(Set(s"endpoints:$name", "metrics:responseTime"), Double.box(responseTime), OffsetDateTime.now(), "endpoint-rate"))

          case _ ⇒
        }
    }
  }
}

