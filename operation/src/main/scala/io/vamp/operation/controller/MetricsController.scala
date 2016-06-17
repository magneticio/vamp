package io.vamp.operation.controller

import io.vamp.common.config.Config
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider, ReplyActor }
import io.vamp.common.notification.NotificationProvider

import scala.concurrent.Future
import scala.language.postfixOps

trait MetricsController extends GatewayDeploymentResolver with EventValue {
  this: ReplyActor with ExecutionContextProvider with ActorSystemProvider with NotificationProvider ⇒

  private val window = Config.duration("vamp.operation.metrics.window")

  def gatewayMetrics(gateway: String, metrics: String): Future[Option[Double]] = gatewayFor(gateway).flatMap {

    case Some(g) ⇒

      last((s"gateways:${g.name}" :: s"metrics:$metrics" :: Nil).toSet, window).map {
        case Some(value) ⇒ Option(value.toString.toDouble)
        case _           ⇒ None
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
