package io.magnetic.vamp_core.operation.deployment

import akka.actor._
import akka.pattern.ask
import io.magnetic.vamp_common.akka.{ActorDescription, ActorExecutionContextProvider, ActorSupport, FutureSupport}
import io.magnetic.vamp_core.model.artifact.Deployment
import io.magnetic.vamp_core.operation.deployment.DeploymentSynchronizationActor.SynchronizeAll
import io.magnetic.vamp_core.operation.deployment.DeploymentWatchdogActor.Period
import io.magnetic.vamp_core.operation.notification.{InternalServerError, OperationNotificationProvider}
import io.magnetic.vamp_core.persistence.PersistenceActor
import io.magnetic.vamp_core.persistence.PersistenceActor.All

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
        implicit val timeout = PersistenceActor.timeout
        timer = Some(context.system.scheduler.schedule(0 milliseconds, period seconds, new Runnable {
          def run() = {
            offLoad(actorFor(PersistenceActor) ? All(classOf[Deployment])) match {
              case deployments: List[_] => actorFor(DeploymentSynchronizationActor) ! SynchronizeAll(deployments.asInstanceOf[List[Deployment]])
              case any => error(InternalServerError(any))
            }
          }
        }))
      } else timer = None
  }

}