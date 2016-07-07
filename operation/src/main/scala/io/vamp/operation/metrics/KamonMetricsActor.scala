package io.vamp.operation.metrics

import io.vamp.common.akka._
import io.vamp.common.vitals._
import io.vamp.operation.notification._
import kamon.Kamon
import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot

import scala.concurrent.Future

case class SystemMetrics(cpu: Option[Cpu], network: Option[Network], processCpu: Option[ProcessCpu], contextSwitches: Option[ContextSwitches])

class KamonMetricsActor extends KamonSupport with CommonSupportForActors with OperationNotificationProvider {

  def receive = {

    case tick: TickMetricSnapshot ⇒ metricSnapshot(tick)

    case StatsRequest             ⇒ reply(Future.successful(None))
  }

  override def preStart() = subscriptions.foreach(subscription ⇒ Kamon.metrics.subscribe(subscription, "**", self, permanently = true))
}
