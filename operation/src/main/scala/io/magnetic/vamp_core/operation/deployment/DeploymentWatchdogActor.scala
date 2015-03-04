package io.magnetic.vamp_core.operation.deployment

import akka.actor._
import akka.pattern.ask
import io.magnetic.vamp_common.akka.{ActorDescription, ActorExecutionContextProvider, ActorSupport, FutureSupport}
import io.magnetic.vamp_core.model.artifact.Deployment
import io.magnetic.vamp_core.operation.deployment.DeploymentWatchdogActor.Period
import io.magnetic.vamp_core.operation.notification.{InternalServerError, OperationNotificationProvider}
import io.magnetic.vamp_core.persistence.PersistenceActor

import scala.concurrent.duration._
import scala.language.postfixOps

object DeploymentWatchdogActor extends ActorDescription {

  def props: Props = Props(new DeploymentWatchdogActor)

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
            offLoad(actorFor(PersistenceActor) ? PersistenceActor.All(classOf[Deployment])) match {
              case deployments: List[_] =>
                val receiver = actorFor(DeploymentSynchronizationActor)
                deployments.asInstanceOf[List[Deployment]].foreach(receiver ! DeploymentSynchronizationActor.Synchronize(_))
                
              case any => error(InternalServerError(any))
            }
          }
        }))
      } else timer = None
  }

}