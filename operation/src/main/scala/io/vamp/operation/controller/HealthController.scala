package io.vamp.operation.controller

import akka.util.Timeout
import io.vamp.common.{ Config, Namespace }

import scala.concurrent.Future

trait HealthController extends AbstractController with EventPeekController {

  private val window = Config.duration("vamp.operation.health.window")

  def gatewayHealth(gatewayName: String)(window: Option[String])(implicit namespace: Namespace, timeout: Timeout): Future[Option[Double]] = {
    peek(s"gateways:$gatewayName" :: "health" :: Nil, windowFrom(window, this.window()))
  }

  def routeHealth(gatewayName: String, routeName: String)(window: Option[String])(implicit namespace: Namespace, timeout: Timeout): Future[Option[Double]] = {
    peek(s"gateways:$gatewayName" :: s"routes:$routeName" :: "health" :: Nil, windowFrom(window, this.window()))
  }

  def deploymentHealth(deploymentName: String)(window: Option[String])(implicit namespace: Namespace, timeout: Timeout): Future[Option[Double]] = {
    peek(s"deployments:$deploymentName" :: "health" :: Nil, windowFrom(window, this.window()))
  }

  def clusterHealth(deploymentName: String, clusterName: String)(window: Option[String])(implicit namespace: Namespace, timeout: Timeout): Future[Option[Double]] = {
    peek(s"deployments:$deploymentName" :: s"clusters:$clusterName" :: "health" :: Nil, windowFrom(window, this.window()))
  }

  def serviceHealth(deploymentName: String, clusterName: String, serviceName: String)(window: Option[String])(implicit namespace: Namespace, timeout: Timeout): Future[Option[Double]] = {
    peek(s"deployments:$deploymentName" :: s"clusters:$clusterName" :: s"services:$serviceName" :: "health" :: Nil, windowFrom(window, this.window()))
  }

  def instanceHealth(deploymentName: String, clusterName: String, serviceName: String, instanceName: String)(window: Option[String])(implicit namespace: Namespace, timeout: Timeout): Future[Option[Double]] = {
    Future.successful(None)
  }
}
