package io.magnetic.vamp_core.operation.deployment

import akka.actor._
import io.vamp.common.akka.{ActorDescription, ActorExecutionContextProvider, ActorSupport, FutureSupport}
import io.magnetic.vamp_core.operation.deployment.DeploymentSynchronizationActor.SynchronizeAll
import io.magnetic.vamp_core.operation.deployment.DeploymentWatchdogActor.Period
import io.magnetic.vamp_core.operation.notification.OperationNotificationProvider

import scala.concurrent.duration._
import scala.language.postfixOps

object DeploymentWatchdogActor extends ActorDescription {

  def props(args: Any*): Props = Props[DeploymentWatchdogActor]

  case class Period(period: Int)

}

class DeploymentWatchdogActor extends Actor with ActorLogging with ActorSupport with FutureSupport with ActorExecutionContextProvider with OperationNotificationProvider {

  private var timer: Option[Cancellable] = None

  def receive: Receive = {
    case Period(period) =>
      timer.map(_.cancel())

      if (period > 0) {
        implicit val actorSystem = context.system
        timer = Some(context.system.scheduler.schedule(0 milliseconds, period seconds, new Runnable {
          def run() = {
            actorFor(DeploymentSynchronizationActor) ! SynchronizeAll
          }
        }))
      } else timer = None
  }

}