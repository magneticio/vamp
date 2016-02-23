package io.vamp.operation.controller

import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider, ReplyActor }
import io.vamp.common.notification.NotificationProvider

import scala.concurrent.Future
import scala.language.postfixOps

trait HealthController extends GatewayDeploymentResolver {
  this: ReplyActor with ExecutionContextProvider with ActorSystemProvider with NotificationProvider ⇒

  def gatewayHealth(gateway: String): Future[Option[Double]] = gatewayFor(gateway) map {
    _.map(_ ⇒ 0D)
  }

  def routeHealth(gateway: String, route: String) = gatewayFor(gateway) map {
    _.flatMap(g ⇒ g.routes.find(_.name == route).map(_ ⇒ 0D))
  }

  def deploymentHealth(deployment: String) = deploymentFor(deployment) map {
    _.map(_ ⇒ 0D)
  }

  def clusterHealth(deployment: String, cluster: String) = deploymentFor(deployment) map {
    _.flatMap(d ⇒ d.clusters.find(_.name == cluster).map(_ ⇒ 0D))
  }

  def serviceHealth(deployment: String, cluster: String, service: String) = deploymentFor(deployment) map {
    _.flatMap(d ⇒ d.clusters.find(_.name == cluster).flatMap(c ⇒ c.services.find(_.breed.name == service).map(_ ⇒ 0D)))
  }

  def instanceHealth(deployment: String, cluster: String, service: String, instance: String) = deploymentFor(deployment) map {
    _.flatMap(d ⇒ d.clusters.find(_.name == cluster).flatMap(c ⇒ c.services.find(_.breed.name == service).flatMap(s ⇒ s.instances.find(_.name == instance).map(_ ⇒ 0D))))
  }
}
