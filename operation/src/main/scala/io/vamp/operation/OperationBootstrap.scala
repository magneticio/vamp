package io.vamp.operation

import akka.actor.{ ActorSystem, Props }
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{ Shutdown, Start }
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

  def run(implicit actorSystem: ActorSystem) = {

    IoC.createActor[DeploymentActor]

    IoC.createActor(Props(classOf[DeploymentSynchronizationActor]).withMailbox(synchronizationMailbox))
    IoC.createActor[DeploymentSynchronizationSchedulerActor] ! SchedulerActor.Period(synchronizationPeriod seconds, 0 seconds)

    IoC.createActor(Props(classOf[GatewaySynchronizationActor]).withMailbox(synchronizationMailbox))
    IoC.createActor[GatewaySynchronizationSchedulerActor] ! SchedulerActor.Period(synchronizationPeriod seconds, synchronizationPeriod / 2 seconds)

    IoC.createActor[SlaActor]
    IoC.createActor[SlaSchedulerActor] ! SchedulerActor.Period(slaPeriod seconds)

    IoC.createActor[EscalationActor]
    IoC.createActor[EscalationSchedulerActor] ! SchedulerActor.Period(escalationPeriod seconds)

    IoC.createActor[EventStreamingActor] ! Start
    IoC.createActor[WorkflowSchedulerActor] ! Start
  }

  override def shutdown(implicit actorSystem: ActorSystem) = {

    IoC.actorFor[EventStreamingActor] ! Shutdown
    IoC.actorFor[WorkflowSchedulerActor] ! Shutdown

    IoC.actorFor[GatewaySynchronizationSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[DeploymentSynchronizationSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[SlaSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[EscalationSchedulerActor] ! SchedulerActor.Period(0 seconds)
  }
}
