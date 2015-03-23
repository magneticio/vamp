package io.vamp.core.operation

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ActorSupport, Bootstrap}
import io.vamp.core.operation.deployment.{DeploymentActor, DeploymentSynchronizationActor, DeploymentWatchdogActor}
import io.vamp.core.operation.sla.{EscalationActor, SlaActor}

object OperationBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    ActorSupport.actorOf(DeploymentActor)
    ActorSupport.actorOf(DeploymentSynchronizationActor)(mailbox = "deployment.deployment-synchronization-mailbox", actorSystem)
    ActorSupport.actorOf(DeploymentWatchdogActor) ! DeploymentWatchdogActor.Period(ConfigFactory.load().getInt("deployment.synchronization.period"))
    ActorSupport.actorOf(SlaActor) ! SlaActor.Period(ConfigFactory.load().getInt("deployment.sla.period"))
    ActorSupport.actorOf(EscalationActor) ! EscalationActor.Period(ConfigFactory.load().getInt("deployment.escalation.period"))
  }
}
