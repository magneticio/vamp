package io.vamp.operation.controller

import akka.util.Timeout
import io.vamp.common.{ Config, Namespace }

import scala.concurrent.Future

trait MetricsController extends AbstractController with EventPeekController {

  private val window = Config.duration("vamp.operation.metrics.window")

  def gatewayMetrics(gatewayName: String, metrics: String)(window: Option[String])(implicit namespace: Namespace, timeout: Timeout): Future[Option[Double]] = {
    peek(s"gateways:$gatewayName" :: s"metrics:$metrics" :: Nil, windowFrom(window, this.window()))
  }

  def routeMetrics(gatewayName: String, routeName: String, metrics: String)(window: Option[String])(implicit namespace: Namespace, timeout: Timeout): Future[Option[Double]] = {
    peek(s"gateways:$gatewayName" :: s"routes:$routeName" :: s"metrics:$metrics" :: Nil, windowFrom(window, this.window()))
  }

  def clusterMetrics(deploymentName: String, clusterName: String, portName: String, metrics: String)(window: Option[String])(implicit namespace: Namespace, timeout: Timeout): Future[Option[Double]] = {
    val gatewayName = s"$deploymentName/$clusterName/$portName"
    peek(s"gateways:$gatewayName" :: s"metrics:$metrics" :: Nil, windowFrom(window, this.window()))
  }

  def serviceMetrics(deploymentName: String, clusterName: String, serviceName: String, portName: String, metrics: String)(window: Option[String])(implicit namespace: Namespace, timeout: Timeout): Future[Option[Double]] = {
    val gatewayName = s"$deploymentName/$clusterName/$portName"
    val routeName = s"$deploymentName/$clusterName/$serviceName"
    peek(s"gateways:$gatewayName" :: s"routes:$routeName" :: s"metrics:$metrics" :: Nil, windowFrom(window, this.window()))
  }

  def instanceMetrics(deployment: String, cluster: String, service: String, instance: String, port: String, metrics: String)(window: Option[String])(implicit namespace: Namespace, timeout: Timeout): Future[Option[Double]] = {
    Future.successful(None)
  }
}
