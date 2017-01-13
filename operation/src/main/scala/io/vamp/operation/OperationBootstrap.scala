package io.vamp.operation

import akka.actor.{ ActorRef, ActorSystem, Props }
import io.vamp.common.akka.{ ActorBootstrap, IoC, SchedulerActor }
import io.vamp.common.config.Config
import io.vamp.operation.config.ConfigurationLoaderActor
import io.vamp.operation.deployment.{ DeploymentActor, DeploymentSynchronizationActor, DeploymentSynchronizationSchedulerActor }
import io.vamp.operation.gateway.{ GatewayActor, GatewaySynchronizationActor, GatewaySynchronizationSchedulerActor }
import io.vamp.operation.metrics.KamonMetricsActor
import io.vamp.operation.sla.{ EscalationActor, EscalationSchedulerActor, SlaActor, SlaSchedulerActor }
import io.vamp.operation.workflow.{ WorkflowActor, WorkflowSynchronizationActor, WorkflowSynchronizationSchedulerActor }

import scala.concurrent.duration._
import scala.language.postfixOps

class OperationBootstrap extends ActorBootstrap {

  val config = "vamp.operation"

  val synchronizationMailbox = "vamp.operation.synchronization.mailbox"

  val slaPeriod = Config.duration(s"$config.sla.period")()

  val escalationPeriod = Config.duration(s"$config.escalation.period")()

  val synchronizationPeriod = Config.duration(s"$config.synchronization.period")()

  val synchronizationInitialDelay = Config.duration(s"$config.synchronization.initial-delay")()

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {

    val actors = List(
      IoC.createActor[ConfigurationLoaderActor],
      IoC.createActor[KamonMetricsActor],

      IoC.createActor[DeploymentActor],

      IoC.createActor(Props(classOf[DeploymentSynchronizationActor]).withMailbox(synchronizationMailbox)),
      IoC.createActor[DeploymentSynchronizationSchedulerActor],

      IoC.createActor[GatewayActor],

      IoC.createActor(Props(classOf[GatewaySynchronizationActor]).withMailbox(synchronizationMailbox)),
      IoC.createActor[GatewaySynchronizationSchedulerActor],

      IoC.createActor[SlaActor],
      IoC.createActor[SlaSchedulerActor],

      IoC.createActor[EscalationActor],
      IoC.createActor[EscalationSchedulerActor],

      IoC.createActor[WorkflowActor],
      IoC.createActor[WorkflowSynchronizationActor],
      IoC.createActor[WorkflowSynchronizationSchedulerActor]
    )

    IoC.actorFor[DeploymentSynchronizationSchedulerActor] ! SchedulerActor.Period(synchronizationPeriod, synchronizationInitialDelay / 3)
    IoC.actorFor[GatewaySynchronizationSchedulerActor] ! SchedulerActor.Period(synchronizationPeriod, synchronizationInitialDelay + 1 * synchronizationPeriod / 3)
    IoC.actorFor[WorkflowSynchronizationSchedulerActor] ! SchedulerActor.Period(synchronizationPeriod, synchronizationInitialDelay + 2 * synchronizationPeriod / 3)

    IoC.actorFor[SlaSchedulerActor] ! SchedulerActor.Period(slaPeriod, synchronizationInitialDelay)
    IoC.actorFor[EscalationSchedulerActor] ! SchedulerActor.Period(escalationPeriod, synchronizationInitialDelay)

    actors
  }

  override def stop(implicit actorSystem: ActorSystem) = {

    IoC.actorFor[DeploymentSynchronizationSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[GatewaySynchronizationSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[WorkflowSynchronizationSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[SlaSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[EscalationSchedulerActor] ! SchedulerActor.Period(0 seconds)

    super.stop(actorSystem)
  }
}
