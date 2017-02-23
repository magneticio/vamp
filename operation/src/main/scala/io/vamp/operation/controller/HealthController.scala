package io.vamp.operation.controller

import io.vamp.common.Config
import io.vamp.common.akka.CommonProvider

import scala.concurrent.Future

trait HealthController extends EventPeekController {
  this: CommonProvider ⇒

  private val window = Config.duration("vamp.operation.health.window")

  def gatewayHealth(gatewayName: String) = {
    peek(s"gateways:$gatewayName" :: "health" :: Nil, window())
  }

  def routeHealth(gatewayName: String, routeName: String) = {
    peek(s"gateways:$gatewayName" :: s"routes:$routeName" :: "health" :: Nil, window())
  }

  def deploymentHealth(deploymentName: String) = {
    peek(s"deployments:$deploymentName" :: "health" :: Nil, window())
  }

  def clusterHealth(deploymentName: String, clusterName: String) = {
    peek(s"deployments:$deploymentName" :: s"clusters:$clusterName" :: "health" :: Nil, window())
  }

  def serviceHealth(deploymentName: String, clusterName: String, serviceName: String) = {
    peek(s"deployments:$deploymentName" :: s"clusters:$clusterName" :: s"services:$serviceName" :: "health" :: Nil, window())
  }

  def instanceHealth(deploymentName: String, clusterName: String, serviceName: String, instanceName: String): Future[Option[Double]] = {
    Future.successful(None)
  }
}
