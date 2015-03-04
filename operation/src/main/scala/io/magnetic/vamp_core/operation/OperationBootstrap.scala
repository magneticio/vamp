package io.magnetic.vamp_core.operation

import akka.actor.ActorSystem
import io.magnetic.vamp_common.akka.{ActorSupport, Bootstrap}
import io.magnetic.vamp_core.operation.deployment.{DeploymentSynchronizationActor, DeploymentActor}

object OperationBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {
    ActorSupport.actorOf(DeploymentActor)
    ActorSupport.actorOf(DeploymentSynchronizationActor)
  }
}
