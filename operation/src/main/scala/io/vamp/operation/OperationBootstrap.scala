package io.vamp.operation

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.util.Timeout
import io.vamp.common.akka.SchedulerActor.Period
import io.vamp.common.akka.{ ActorBootstrap, IoC, SchedulerActor }
import io.vamp.common.{ Config, Namespace }
import io.vamp.operation.config.ConfigurationLoaderActor
import io.vamp.operation.deployment.{ DeploymentActor, DeploymentSynchronizationActor, DeploymentSynchronizationSchedulerActor }
import io.vamp.operation.gateway.{ GatewayActor, GatewaySynchronizationActor, GatewaySynchronizationSchedulerActor }
import io.vamp.operation.metrics.KamonMetricsActor
import io.vamp.operation.sla.{ EscalationActor, EscalationSchedulerActor, SlaActor, SlaSchedulerActor }
import io.vamp.operation.workflow.{ WorkflowActor, WorkflowSynchronizationActor, WorkflowSynchronizationSchedulerActor }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

class OperationBootstrap extends ActorBootstrap {

  val config = "vamp.operation"

  val synchronizationMailbox = "vamp.operation.synchronization.mailbox"

  def createActors(implicit actorSystem: ActorSystem, namespace: Namespace, timeout: Timeout): Future[List[ActorRef]] = {
    implicit val ec: ExecutionContext = actorSystem.dispatcher
    implicit val delay = Config.duration(s"$config.synchronization.initial-delay")()

    val slaPeriod = Config.duration(s"$config.sla.period")()
    val escalationPeriod = Config.duration(s"$config.escalation.period")()
    val synchronizationPeriod = Config.duration(s"$config.synchronization.period")()

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

    kick(classOf[DeploymentSynchronizationSchedulerActor], Period(synchronizationPeriod))
    kick(classOf[GatewaySynchronizationSchedulerActor], Period(synchronizationPeriod, synchronizationPeriod / 3))
    kick(classOf[WorkflowSynchronizationSchedulerActor], Period(synchronizationPeriod, 2 * synchronizationPeriod / 3))

    kick(classOf[SlaSchedulerActor], Period(slaPeriod))
    kick(classOf[EscalationSchedulerActor], Period(escalationPeriod))

    Future.sequence(actors)
  }

  override def stop(implicit actorSystem: ActorSystem, namespace: Namespace) = {

    IoC.actorFor[DeploymentSynchronizationSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[GatewaySynchronizationSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[WorkflowSynchronizationSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[SlaSchedulerActor] ! SchedulerActor.Period(0 seconds)
    IoC.actorFor[EscalationSchedulerActor] ! SchedulerActor.Period(0 seconds)

    super.stop(actorSystem, namespace)
  }

  private def kick(clazz: Class[_], period: Period)(implicit actorSystem: ActorSystem, namespace: Namespace, delay: FiniteDuration): Unit = {
    actorSystem.scheduler.scheduleOnce(delay)({
      IoC.actorFor(clazz) ! period
    })(actorSystem.dispatcher)
  }
}
