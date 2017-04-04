package io.vamp.common.akka

import akka.actor._
import io.vamp.common.akka.SchedulerActor.{ Period, Tick }

import scala.concurrent.duration._
import scala.language.postfixOps

object SchedulerActor {

  case class Period(interval: FiniteDuration, initialDelay: FiniteDuration = 0 seconds)

  object Tick

}

trait SchedulerActor extends ScheduleSupport with CommonSupportForActors {

  def receive: Receive = {
    case Tick                           ⇒ tick()
    case Period(_period, _initialDelay) ⇒ schedule(_period, _initialDelay)
  }

  def tick(): Unit
}

trait ScheduleSupport {
  this: Actor with ExecutionContextProvider ⇒

  protected var period: FiniteDuration = 0.seconds

  private var timer: Option[Cancellable] = None

  def schedule(period: FiniteDuration, initialDelay: FiniteDuration = 0 seconds) = {
    timer.map(_.cancel())
    timer = if (period.toNanos > 0) {
      this.period = period
      Some(context.system.scheduler.schedule(initialDelay, period, self, Tick))
    }
    else {
      this.period = 0.seconds
      None
    }
  }

  def unschedule() = timer.map(_.cancel())
}