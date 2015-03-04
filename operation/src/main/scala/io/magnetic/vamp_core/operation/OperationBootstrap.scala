package io.magnetic.vamp_core.operation

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_common.akka.{ActorSupport, Bootstrap}
import io.magnetic.vamp_core.operation.deployment.{DeploymentActor, DeploymentSynchronizationActor, DeploymentWatchdogActor}

object OperationBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    ActorSupport.actorOf(DeploymentActor)
    ActorSupport.actorOf(DeploymentSynchronizationActor)
    ActorSupport.actorOf(DeploymentWatchdogActor) ! DeploymentWatchdogActor.Period(ConfigFactory.load().getInt("deployment.watchdog.period"))
  }
}
