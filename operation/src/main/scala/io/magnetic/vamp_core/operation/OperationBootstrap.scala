package io.magnetic.vamp_core.operation

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ActorSupport, Bootstrap}
import io.magnetic.vamp_core.operation.deployment.{DeploymentActor, DeploymentSynchronizationActor, DeploymentWatchdogActor}
import io.magnetic.vamp_core.operation.sla.SlaMonitorActor

object OperationBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    ActorSupport.actorOf(DeploymentActor)
    ActorSupport.actorOf(DeploymentSynchronizationActor)(mailbox = "deployment.deployment-synchronization-mailbox", actorSystem)
    ActorSupport.actorOf(DeploymentWatchdogActor) ! DeploymentWatchdogActor.Period(ConfigFactory.load().getInt("deployment.synchronization.period"))
    ActorSupport.actorOf(SlaMonitorActor) ! SlaMonitorActor.Period(ConfigFactory.load().getInt("deployment.sla.period"))
  }
}
