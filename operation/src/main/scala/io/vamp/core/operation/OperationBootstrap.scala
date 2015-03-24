package io.vamp.core.operation

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ActorSupport, Bootstrap, SchedulerActor}
import io.vamp.core.operation.deployment.{DeploymentActor, DeploymentSynchronizationActor, DeploymentSynchronizationSchedulerActor}
import io.vamp.core.operation.sla.{EscalationActor, EscalationSchedulerActor, SlaActor, SlaSchedulerActor}

import scala.concurrent.duration._
import scala.language.postfixOps

object OperationBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {

    ActorSupport.actorOf(DeploymentActor)

    ActorSupport.actorOf(DeploymentSynchronizationActor)(mailbox = "deployment.deployment-synchronization-mailbox", actorSystem)
    ActorSupport.actorOf(DeploymentSynchronizationSchedulerActor) ! SchedulerActor.Period(ConfigFactory.load().getInt("deployment.synchronization.period") seconds)

    ActorSupport.actorOf(SlaActor)
    ActorSupport.actorOf(SlaSchedulerActor) ! SchedulerActor.Period(ConfigFactory.load().getInt("deployment.sla.period") seconds)

    ActorSupport.actorOf(EscalationActor)
    ActorSupport.actorOf(EscalationSchedulerActor) ! SchedulerActor.Period(ConfigFactory.load().getInt("deployment.escalation.period") seconds)
  }
}
