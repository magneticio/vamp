package io.vamp.operation.controller

import akka.util.Timeout
import io.vamp.common.{ Config, Namespace }

import scala.concurrent.Future

trait HealthController extends AbstractController with EventPeekController {

  private val window = Config.duration("vamp.operation.health.window")

  def gatewayHealth(gatewayName: String)(implicit namespace: Namespace, timeout: Timeout) = {
    peek(s"gateways:$gatewayName" :: "health" :: Nil, window())
  }

  def routeHealth(gatewayName: String, routeName: String)(implicit namespace: Namespace, timeout: Timeout) = {
    peek(s"gateways:$gatewayName" :: s"routes:$routeName" :: "health" :: Nil, window())
  }

  def deploymentHealth(deploymentName: String)(implicit namespace: Namespace, timeout: Timeout) = {
    peek(s"deployments:$deploymentName" :: "health" :: Nil, window())
  }

  def clusterHealth(deploymentName: String, clusterName: String)(implicit namespace: Namespace, timeout: Timeout) = {
    peek(s"deployments:$deploymentName" :: s"clusters:$clusterName" :: "health" :: Nil, window())
  }

  def serviceHealth(deploymentName: String, clusterName: String, serviceName: String)(implicit namespace: Namespace, timeout: Timeout) = {
    peek(s"deployments:$deploymentName" :: s"clusters:$clusterName" :: s"services:$serviceName" :: "health" :: Nil, window())
  }

  def instanceHealth(deploymentName: String, clusterName: String, serviceName: String, instanceName: String)(implicit namespace: Namespace, timeout: Timeout): Future[Option[Double]] = {
    Future.successful(None)
  }
}
