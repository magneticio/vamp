package io.vamp.common.akka

import akka.actor._
import io.vamp.common.akka.SchedulerActor.{Period, Tick}

import scala.concurrent.duration._
import scala.language.postfixOps

object SchedulerActor {

  case class Period(period: FiniteDuration)

  object Tick

}

abstract class SchedulerActor extends CommonSupportForActors {

  private var timer: Option[Cancellable] = None

  def receive: Receive = {
    case Tick => tick()
    case Period(period) => schedule(period)
  }

  def schedule(period: FiniteDuration) = {
    timer.map(_.cancel())
    timer = if (period.toNanos > 0) Some(context.system.scheduler.schedule(0 seconds, period, self, Tick)) else None
  }

  def tick(): Unit
}