package io.vamp.gateway_driver.aggregation

import java.time.OffsetDateTime

import akka.actor._
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.gateway_driver.logstash.Logstash
import io.vamp.gateway_driver.notification.GatewayDriverNotificationProvider
import io.vamp.model.artifact._
import io.vamp.model.event.Event
import io.vamp.persistence.db.{ ArtifactPaginationSupport, PersistenceActor }
import io.vamp.pulse.PulseActor.Publish
import io.vamp.pulse.{ ElasticsearchClient, PulseActor, PulseEvent }

import scala.concurrent.Future
import scala.language.postfixOps

class MetricsSchedulerActor extends SchedulerActor with GatewayDriverNotificationProvider {

  def tick() = IoC.actorFor[MetricsActor] ! MetricsActor.MetricsUpdate
}

object MetricsActor {

  object MetricsUpdate

  val window = ConfigFactory.load().getInt("vamp.gateway-driver.aggregation.window")
}

/**
 * This should be implemented as a workflow.
 */
class MetricsActor extends PulseEvent with ArtifactPaginationSupport with CommonSupportForActors with GatewayDriverNotificationProvider {

  private case class Metrics(count: Long, rate: Double, value: Double)

  import MetricsActor._

  implicit val timeout = PersistenceActor.timeout

  private val es = new ElasticsearchClient(PulseActor.elasticsearchUrl)

  def receive: Receive = {
    case MetricsUpdate ⇒ allArtifacts[Gateway] map process
    case _             ⇒
  }

  private def process: (List[Gateway] ⇒ Unit) = { gateways ⇒
    gateways.foreach { gateway ⇒

      healthMetrics(gateway.lookupName) map {
        case Metrics(_, _, health) ⇒
          publish(s"gateways:${gateway.lookupName}" :: "health" :: Nil, health)
      }

      responseMetrics(gateway.lookupName) map {
        case Metrics(_, rate, responseTime) ⇒
          publish(s"gateways:${gateway.lookupName}" :: "metrics:rate" :: Nil, rate)
          publish(s"gateways:${gateway.lookupName}" :: "metrics:responseTime" :: Nil, responseTime)
      }
    }
  }

  private def responseMetrics(lookup: String): Future[Metrics] = {
    es.search[Any](Logstash.index, Logstash.`type`,
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
         |                "ft": "$lookup"
         |              }
         |            },
         |            {
         |              "range": {
         |                "@timestamp": {
         |                  "gt": "now-${window}s"
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

          val rate: Double = count.toDouble / window

          val responseTime: Double = map.asInstanceOf[Map[String, _]].get("aggregations").flatMap(map ⇒ map.asInstanceOf[Map[String, _]].get("Tt")).flatMap(map ⇒ map.asInstanceOf[Map[String, _]].get("value")) match {
            case Some(number: BigInt)     ⇒ number.toDouble
            case Some(number: BigDecimal) ⇒ number.toDouble
            case _                        ⇒ 0D
          }

          log.debug(s"Request count/rate/responseTime for $lookup: $count/$rate/$responseTime")

          Metrics(count, rate, responseTime)

        case _ ⇒ Metrics(0, 0D, 0D)
      }
  }

  private def healthMetrics(lookup: String): Future[Metrics] = {

    val errorCode = 500

    es.search[Any](Logstash.index, Logstash.`type`,
      s"""
         {
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
         |                "ft": "$lookup"
         |              }
         |            },
         |            {
         |              "range": {
         |                "ST": {
         |                  "gte": "$errorCode"
         |                }
         |              }
         |            },
         |            {
         |              "range": {
         |                "@timestamp": {
         |                  "gt": "now-${window}s"
         |                }
         |              }
         |            }
         |          ]
         |        }
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

          val health = if (count > 0) 0 else 1

          val rate: Double = count.toDouble / window

          log.debug(s"Request 500 error count/rate/health for $lookup: $count/$rate/$health")

          Metrics(count, rate, health)

        case _ ⇒ Metrics(0, 0D, 1D)
      }
  }

  private def publish(tags: List[String], value: Double) = actorFor[PulseActor] ! Publish(Event(tags.toSet, Double.box(value), OffsetDateTime.now(), "gateway-metrics"))
}