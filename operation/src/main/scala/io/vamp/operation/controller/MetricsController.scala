package io.vamp.operation.controller

import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.config.Config
import io.vamp.common.notification.NotificationProvider

import scala.concurrent.Future

trait MetricsController extends EventPeekController {
  this: ExecutionContextProvider with ActorSystemProvider with NotificationProvider ⇒

  private val window = Config.duration("vamp.operation.metrics.window")()

  def gatewayMetrics(gatewayName: String, metrics: String) = {
    peek(s"gateways:$gatewayName" :: s"metrics:$metrics" :: Nil, window)
  }

  def routeMetrics(gatewayName: String, routeName: String, metrics: String) = {
    peek(s"gateways:$gatewayName" :: s"routes:$routeName" :: s"metrics:$metrics" :: Nil, window)
  }

  def clusterMetrics(deploymentName: String, clusterName: String, portName: String, metrics: String) = {
    val gatewayName = s"$deploymentName/$clusterName/$portName"
    peek(s"gateways:$gatewayName" :: s"metrics:$metrics" :: Nil, window)
  }

  def serviceMetrics(deploymentName: String, clusterName: String, serviceName: String, portName: String, metrics: String) = {
    val gatewayName = s"$deploymentName/$clusterName/$portName"
    val routeName = s"$deploymentName/$clusterName/$serviceName"
    peek(s"gateways:$gatewayName" :: s"routes:$routeName" :: s"metrics:$metrics" :: Nil, window)
  }

  def instanceMetrics(deployment: String, cluster: String, service: String, instance: String, port: String, metrics: String): Future[Option[Double]] = {
    Future.successful(None)
  }
}
