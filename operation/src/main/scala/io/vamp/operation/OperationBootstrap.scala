package io.vamp.operation

import akka.actor.{ ActorRef, ActorSystem, Props }
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.{ Bootstrap, IoC, SchedulerActor }
import io.vamp.operation.deployment.{ DeploymentActor, DeploymentSynchronizationActor, DeploymentSynchronizationSchedulerActor }
import io.vamp.operation.gateway.{ GatewaySynchronizationActor, GatewaySynchronizationSchedulerActor }
import io.vamp.operation.sla.{ EscalationActor, EscalationSchedulerActor, SlaActor, SlaSchedulerActor }
import io.vamp.operation.sse.EventStreamingActor
import io.vamp.operation.workflow.WorkflowSchedulerActor

import scala.concurrent.duration._
import scala.language.postfixOps

object OperationBootstrap extends Bootstrap {

  val configuration = ConfigFactory.load().getConfig("vamp.operation")

  val synchronizationMailbox = "vamp.operation.synchronization.mailbox"

  val slaPeriod = configuration.getInt("sla.period")

  val escalationPeriod = configuration.getInt("escalation.period")

  val synchronizationPeriod = configuration.getInt("synchronization.period")

  val synchronizationInitialDelay = configuration.getInt("synchronization.initial-delay")

  def createActors(implicit actorSystem: ActorSystem): List[ActorRef] = {
    val actors = List(
      IoC.createActor[DeploymentActor],

      IoC.createActor(Props(classOf[DeploymentSynchronizationActor]).withMailbox(synchronizationMailbox)),
      IoC.createActor[DeploymentSynchronizationSchedulerActor],

      IoC.createActor(Props(classOf[GatewaySynchronizationActor]).withMailbox(synchronizationMailbox)),
      IoC.createActor[GatewaySynchronizationSchedulerActor],

      IoC.createActor[SlaActor],
      IoC.createActor[SlaSchedulerActor],

      IoC.createActor[EscalationActor],
      IoC.createActor[EscalationSchedulerActor],

      IoC.createActor[EventStreamingActor],
      IoC.createActor[WorkflowSchedulerActor]
    )

    IoC.actorFor[DeploymentSynchronizationSchedulerActor] ! SchedulerActor.Period(synchronizationPeriod seconds, synchronizationInitialDelay seconds)
    IoC.actorFor[GatewaySynchronizationSchedulerActor] ! SchedulerActor.Period(synchronizationPeriod seconds, (synchronizationInitialDelay + synchronizationPeriod / 2) seconds)
    IoC.actorFor[SlaSchedulerActor] ! SchedulerActor.Period(slaPeriod seconds)
    IoC.actorFor[EscalationSchedulerActor] ! SchedulerActor.Period(escalationPeriod seconds)

    actors
  }

  override def shutdown(implicit actorSystem: ActorSystem): Unit = {

    IoC.actorFor[GatewaySynchronizationSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[DeploymentSynchronizationSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[SlaSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[EscalationSchedulerActor] ! SchedulerActor.Period(0 seconds)

    super.shutdown(actorSystem)
  }
}
