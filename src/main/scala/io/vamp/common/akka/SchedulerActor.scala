package io.vamp.common.akka

import akka.actor._
import io.vamp.common.akka.SchedulerActor.Period

import scala.concurrent.duration._
import scala.language.postfixOps

object SchedulerActor {

  case class Period(period: Int)

}

abstract class SchedulerActor extends Actor with ActorLogging with ActorSupport with FutureSupport with ActorExecutionContextProvider {

  private var timer: Option[Cancellable] = None

  def receive: Receive = {
    case Period(period) =>
      timer.map(_.cancel())

      if (period > 0) {
        implicit val actorSystem = context.system
        timer = Some(context.system.scheduler.schedule(0 milliseconds, period seconds, new Runnable {
          def run() = {
            tick()
          }
        }))
      } else timer = None
  }

  def tick(): Unit
}