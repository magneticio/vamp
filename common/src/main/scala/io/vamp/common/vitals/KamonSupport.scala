package io.vamp.common.vitals

import kamon.metric.SubscriptionsDispatcher.TickMetricSnapshot
import kamon.metric._
import kamon.metric.instrument.Histogram

case class Cpu(user: Double, system: Double, waiting: Double, idle: Double)

case class Network(rxBytes: Double, txBytes: Double, rxErrors: Long, txErrors: Long)

case class ProcessCpu(user: Double, total: Double)

case class ContextSwitches(global: Double, perProcessNonVoluntary: Double, perProcessVoluntary: Double)

trait KamonSupport {

  protected var cpu: Option[Cpu] = None
  protected var network: Option[Network] = None
  protected var processCpu: Option[ProcessCpu] = None
  protected var contextSwitches: Option[ContextSwitches] = None

  protected def subscriptions: List[String] = "system-metric" :: Nil

  protected def metricSnapshot(tick: TickMetricSnapshot): Unit = tick.metrics foreach {
    case (entity, snapshot) if entity.category == "system-metric" ⇒ systemMetrics(entity.name, snapshot)
    case _ ⇒
  }

  private def systemMetrics(metric: String, snapshot: EntitySnapshot): Unit = metric match {
    case "cpu"              ⇒ cpu = cpuMetrics(snapshot)
    case "network"          ⇒ network = networkMetrics(snapshot)
    case "process-cpu"      ⇒ processCpu = processCpuMetrics(snapshot)
    case "context-switches" ⇒ contextSwitches = contextSwitchesMetrics(snapshot)
    case _                  ⇒
  }

  private def cpuMetrics(cpuMetrics: EntitySnapshot): Option[Cpu] = for {
    user ← cpuMetrics.histogram("cpu-user")
    system ← cpuMetrics.histogram("cpu-system")
    cpuWait ← cpuMetrics.histogram("cpu-wait")
    idle ← cpuMetrics.histogram("cpu-idle")
  } yield Cpu(user.average, system.average, cpuWait.average, idle.average)

  private def networkMetrics(networkMetrics: EntitySnapshot): Option[Network] = for {
    rxBytes ← networkMetrics.histogram("rx-bytes")
    txBytes ← networkMetrics.histogram("tx-bytes")
    rxErrors ← networkMetrics.histogram("rx-errors")
    txErrors ← networkMetrics.histogram("tx-errors")
  } yield Network(rxBytes.average, txBytes.average, rxErrors.sum, txErrors.sum)

  private def processCpuMetrics(processCpuMetrics: EntitySnapshot): Option[ProcessCpu] = for {
    user ← processCpuMetrics.histogram("process-user-cpu")
    total ← processCpuMetrics.histogram("process-cpu")
  } yield ProcessCpu(user.average, total.average)

  private def contextSwitchesMetrics(contextSwitchMetrics: EntitySnapshot): Option[ContextSwitches] = for {
    perProcessVoluntary ← contextSwitchMetrics.histogram("context-switches-process-voluntary")
    perProcessNonVoluntary ← contextSwitchMetrics.histogram("context-switches-process-non-voluntary")
    global ← contextSwitchMetrics.histogram("context-switches-global")
  } yield ContextSwitches(global.average, perProcessNonVoluntary.average, perProcessVoluntary.average)

  private implicit class RichHistogramSnapshot(histogram: Histogram.Snapshot) {
    def average: Double = {
      if (histogram.numberOfMeasurements == 0) 0D else histogram.sum / histogram.numberOfMeasurements
    }
  }

}
