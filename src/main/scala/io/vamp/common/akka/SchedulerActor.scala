package io.vamp.common.akka

import akka.actor._
import io.vamp.common.akka.SchedulerActor.{Period, Tick}

import scala.concurrent.duration._
import scala.language.postfixOps

object SchedulerActor {

  case class Period(interval: FiniteDuration, initialDelay: FiniteDuration = 0 seconds)

  object Tick

}

abstract class SchedulerActor extends ScheduleSupport with CommonSupportForActors {

  def receive: Receive = {
    case Tick => tick()
    case Period(interval, initialDelay) => schedule(interval, initialDelay)
  }

  def tick(): Unit
}

trait ScheduleSupport {
  this: Actor with ExecutionContextProvider =>

  private var timer: Option[Cancellable] = None

  def schedule(interval: FiniteDuration, initialDelay: FiniteDuration = 0 seconds) = {
    timer.map(_.cancel())
    timer = if (interval.toNanos > 0) Some(context.system.scheduler.schedule(initialDelay, interval, self, Tick)) else None
  }

  def unschedule() = timer.map(_.cancel())
}