package io.vamp.common.akka

import akka.actor._
import io.vamp.common.akka.SchedulerActor.Period

import scala.concurrent.duration._
import scala.language.postfixOps

object SchedulerActor {

  case class Period(period: FiniteDuration)

}

abstract class SchedulerActor extends CommonActorSupport {

  private var timer: Option[Cancellable] = None

  def receive: Receive = {
    case Period(period) => schedule(period)
  }

  def schedule(period: FiniteDuration) = {
    timer.map(_.cancel())
    if (period.toNanos > 0) {
      timer = Some(context.system.scheduler.schedule(0 seconds, period, new Runnable {
        def run() = {
          tick()
        }
      }))
    } else timer = None
  }

  def tick(): Unit
}