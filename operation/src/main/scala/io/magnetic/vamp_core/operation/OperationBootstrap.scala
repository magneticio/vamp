package io.magnetic.vamp_core.operation

import akka.actor.{Actor, ActorRef, ActorSystem}
import com.typesafe.config.ConfigFactory
import io.magnetic.vamp_common.akka.{ActorSupport, Bootstrap}
import io.magnetic.vamp_core.operation.deployment.{DeploymentActor, DeploymentSynchronizationActor}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

object OperationBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {

    ActorSupport.actorOf(DeploymentActor)

    val deploymentSynchronization = ActorSupport.actorOf(DeploymentSynchronizationActor)

    val watchdog = ConfigFactory.load().getInt("deployment.watchdog.period")
    
    if (watchdog > 0) {
      implicit val sender: ActorRef = Actor.noSender
      implicit val executor: ExecutionContext = actorSystem.dispatcher
      actorSystem.scheduler.schedule(0 milliseconds, watchdog seconds, deploymentSynchronization, DeploymentSynchronizationActor.SynchronizeAll)
    }
  }
}
