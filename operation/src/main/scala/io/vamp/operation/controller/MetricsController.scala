package io.vamp.operation.controller

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.IoC._
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider, ReplyActor }
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.event._
import io.vamp.pulse.{ EventRequestEnvelope, EventResponseEnvelope, PulseActor }
import akka.pattern.ask

import scala.concurrent.Future
import scala.language.postfixOps

trait MetricsController extends GatewayDeploymentResolver {
  this: ReplyActor with ExecutionContextProvider with ActorSystemProvider with NotificationProvider ⇒

  private val window = ConfigFactory.load().getInt("vamp.operation.metrics.window")

  def gatewayMetrics(gateway: String, metrics: String): Future[Option[Double]] = gatewayFor(gateway).flatMap {

    case Some(g) ⇒

      val now = OffsetDateTime.now()
      val from = now.minus(window, ChronoUnit.SECONDS)

      val tags = s"gateways:${g.lookupName}" :: s"metrics:$metrics" :: Nil

      val eventQuery = EventQuery(tags.toSet, Some(TimeRange(Some(from), Some(now), includeLower = true, includeUpper = true)), None)

      actorFor[PulseActor] ? PulseActor.Query(EventRequestEnvelope(eventQuery, 1, 1)) map {
        case EventResponseEnvelope(Event(_, value, _, _) :: tail, _, _, _) ⇒ Option(value.toString.toDouble)
        case other ⇒ None
      }

    case None ⇒ Future.successful(None)
  }

  def routeMetrics(gateway: String, route: String, metrics: String) = gatewayFor(gateway) map {
    _.flatMap(g ⇒ g.routes.find(_.name == route).map(_ ⇒ 0D))
  }

  def clusterMetrics(deployment: String, cluster: String, port: String, metrics: String) = deploymentFor(deployment) map {
    _.flatMap(d ⇒ d.clusters.find(_.name == cluster).map(_ ⇒ 0D))
  }

  def serviceMetrics(deployment: String, cluster: String, service: String, port: String, metrics: String) = deploymentFor(deployment) map {
    _.flatMap(d ⇒ d.clusters.find(_.name == cluster).flatMap(c ⇒ c.services.find(_.breed.name == service).map(_ ⇒ 0D)))
  }

  def instanceMetrics(deployment: String, cluster: String, service: String, instance: String, port: String, metrics: String) = deploymentFor(deployment) map {
    _.flatMap(d ⇒ d.clusters.find(_.name == cluster).flatMap(c ⇒ c.services.find(_.breed.name == service).flatMap(s ⇒ s.instances.find(_.name == instance).map(_ ⇒ 0D))))
  }
}
