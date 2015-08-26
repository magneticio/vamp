package io.vamp.core.operation

import akka.actor.{ ActorSystem, Props }
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{ Shutdown, Start }
import io.vamp.common.akka.{ Bootstrap, IoC, SchedulerActor }
import io.vamp.core.operation.deployment.{ DeploymentActor, DeploymentSynchronizationActor, DeploymentSynchronizationSchedulerActor }
import io.vamp.core.operation.sla.{ EscalationActor, EscalationSchedulerActor, SlaActor, SlaSchedulerActor }
import io.vamp.core.operation.sse.{ EventSteamingActor, SseConsumerActor }
import io.vamp.core.operation.workflow.{ WorkflowConfiguration, WorkflowSchedulerActor }

import scala.concurrent.duration._
import scala.language.postfixOps

object OperationBootstrap extends Bootstrap {

  def run(implicit actorSystem: ActorSystem) = {

    IoC.createActor(Props(classOf[DeploymentActor]))

    IoC.createActor(Props(classOf[DeploymentSynchronizationActor]).withMailbox("vamp.core.operation.synchronization.mailbox"))
    IoC.createActor(Props(classOf[DeploymentSynchronizationSchedulerActor])) ! SchedulerActor.Period(ConfigFactory.load().getInt("vamp.core.operation.synchronization.period") seconds)

    IoC.createActor(Props(classOf[SlaActor]))
    IoC.createActor(Props(classOf[SlaSchedulerActor])) ! SchedulerActor.Period(ConfigFactory.load().getInt("vamp.core.operation.sla.period") seconds)

    IoC.createActor(Props(classOf[EscalationActor]))
    IoC.createActor(Props(classOf[EscalationSchedulerActor])) ! SchedulerActor.Period(ConfigFactory.load().getInt("vamp.core.operation.escalation.period") seconds)

    if (WorkflowConfiguration.enabled) IoC.createActor(Props(classOf[WorkflowSchedulerActor])) ! Start

    IoC.createActor(Props(classOf[SseConsumerActor])) ! Start
    IoC.createActor(Props(classOf[EventSteamingActor])) ! Start
  }

  override def shutdown(implicit actorSystem: ActorSystem) = {
    if (WorkflowConfiguration.enabled) IoC.actorFor[WorkflowSchedulerActor] ! Shutdown

    IoC.actorFor[EventSteamingActor] ! Shutdown
    IoC.actorFor[SseConsumerActor] ! Shutdown
  }
}
