package io.vamp.core.operation.deployment

import akka.actor._
import io.vamp.common.akka._
import io.vamp.core.operation.deployment.DeploymentSynchronizationActor.SynchronizeAll
import io.vamp.core.operation.notification.OperationNotificationProvider

import scala.language.postfixOps

object DeploymentSchedulerActor extends ActorDescription {

  def props(args: Any*): Props = Props[DeploymentSchedulerActor]

}

class DeploymentSchedulerActor extends SchedulerActor with OperationNotificationProvider {

  def tick() = actorFor(DeploymentSynchronizationActor) ! SynchronizeAll
}