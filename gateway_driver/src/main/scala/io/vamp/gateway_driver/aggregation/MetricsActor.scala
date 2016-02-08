package io.vamp.gateway_driver.aggregation

import java.time.OffsetDateTime

import akka.actor._
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common.crypto.Hash
import io.vamp.gateway_driver.GatewayMarshaller
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
 * Workaround for current (old) UI and showing metrics.
 * This is a naive implementation for aggregation of response time and request rate of gateways.
 * This should be removed once we have an updated UI.
 */
class MetricsActor extends PulseEvent with ArtifactPaginationSupport with CommonSupportForActors with GatewayDriverNotificationProvider {

  private case class Metrics(rate: Double, responseTime: Double)

  import MetricsActor._

  implicit val timeout = PersistenceActor.timeout

  private val es = new ElasticsearchClient(PulseActor.elasticsearchUrl)

  private val referenceMatcher = """^[a-zA-Z0-9][a-zA-Z0-9.\-_]{3,63}$""".r

  def receive: Receive = {
    case MetricsUpdate ⇒ allArtifacts[Deployment] map (gateways andThen services)
    case _             ⇒
  }

  private def gateways: (List[Deployment] ⇒ List[Deployment]) = { (deployments: List[Deployment]) ⇒
    deployments.foreach { deployment ⇒
      deployment.gateways.foreach { gateway ⇒
        val name = nameFor(deployment, gateway.port)
        query(gateway.lookupName) map {
          case Metrics(rate, responseTime) ⇒
            publish(s"gateways:$name" :: "metrics:rate" :: Nil, rate)
            publish(s"gateways:$name" :: "metrics:responseTime" :: Nil, responseTime)
        }
      }
    }
    deployments
  }

  private def services: (List[Deployment] ⇒ List[Deployment]) = { (deployments: List[Deployment]) ⇒
    deployments.foreach { deployment ⇒
      deployment.clusters.foreach { cluster ⇒
        cluster.services.filter(_.state.isDeployed).foreach { service ⇒
          service.breed.ports.foreach { port ⇒
            cluster.routingBy(port.name) foreach {
              case gateway ⇒
                gateway.routes.map(_.path.segments).find {
                  case _ :: _ :: s :: _ :: Nil ⇒ s == service.breed.name
                  case _                       ⇒ false
                } foreach {
                  case segments ⇒ query(GatewayMarshaller.lookup(gateway, segments)) map {
                    case Metrics(rate, responseTime) ⇒
                      publish(s"gateways:${deployment.name}_${cluster.name}_${port.name}" :: s"services:${service.breed.name}" :: "service" :: "metrics:rate" :: Nil, rate)
                      publish(s"gateways:${deployment.name}_${cluster.name}_${port.name}" :: s"services:${service.breed.name}" :: "service" :: "metrics:responseTime" :: Nil, responseTime)
                  }
                }
            }
          }
        }
      }
    }
    deployments
  }

  private def nameFor(deployment: Deployment, port: Port): String = flatten(path2string(deployment.name :: port.name :: Nil))

  private def path2string(path: GatewayPath): String = {
    path.segments match {
      case Nil                   ⇒ ""
      case some if some.size < 4 ⇒ path2string(some :+ "_")
      case some                  ⇒ some.mkString("/")
    }
  }

  private def flatten(string: String) = {
    val flatten = string.replaceAll("[^\\p{L}\\d_]", "_")
    flatten match {
      case referenceMatcher(_*) ⇒ flatten
      case _                    ⇒ Hash.hexSha1(flatten)
    }
  }

  private def query(lookup: String): Future[Metrics] = {
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

          Metrics(rate, responseTime)

        case _ ⇒ Metrics(0D, 0D)
      }
  }

  private def publish(tags: List[String], value: Double) = actorFor[PulseActor] ! Publish(Event(tags.toSet, Double.box(value), OffsetDateTime.now(), "gateway-metrics"))
}