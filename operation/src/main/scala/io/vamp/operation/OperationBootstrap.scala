package io.vamp.operation

import akka.actor.{ ActorSystem, Props }
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{ Shutdown, Start }
import io.vamp.common.akka.{ Bootstrap, IoC, SchedulerActor }
import io.vamp.operation.deployment.{ DeploymentActor, DeploymentSynchronizationActor, DeploymentSynchronizationSchedulerActor }
import io.vamp.operation.sla.{ EscalationActor, EscalationSchedulerActor, SlaActor, SlaSchedulerActor }
import io.vamp.operation.sse.EventStreamingActor
import io.vamp.operation.workflow.{ WorkflowConfiguration, WorkflowSchedulerActor }

import scala.concurrent.duration._
import scala.language.postfixOps

object OperationBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {

    IoC.createActor[DeploymentActor]

    IoC.createActor(Props(classOf[DeploymentSynchronizationActor]).withMailbox("vamp.operation.synchronization.mailbox"))
    IoC.createActor[DeploymentSynchronizationSchedulerActor] ! SchedulerActor.Period(ConfigFactory.load().getInt("vamp.operation.synchronization.period") seconds)

    IoC.createActor[SlaActor]
    IoC.createActor[SlaSchedulerActor] ! SchedulerActor.Period(ConfigFactory.load().getInt("vamp.operation.sla.period") seconds)

    IoC.createActor[EscalationActor]
    IoC.createActor[EscalationSchedulerActor] ! SchedulerActor.Period(ConfigFactory.load().getInt("vamp.operation.escalation.period") seconds)

    if (WorkflowConfiguration.enabled) IoC.createActor[WorkflowSchedulerActor] ! Start

    IoC.createActor[EventStreamingActor] ! Start
  }

  override def shutdown(implicit actorSystem: ActorSystem) = {
    if (WorkflowConfiguration.enabled) IoC.actorFor[WorkflowSchedulerActor] ! Shutdown

    IoC.actorFor[EventStreamingActor] ! Shutdown
    IoC.actorFor[DeploymentSynchronizationSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[SlaSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[EscalationSchedulerActor] ! SchedulerActor.Period(0 seconds)
  }
}
