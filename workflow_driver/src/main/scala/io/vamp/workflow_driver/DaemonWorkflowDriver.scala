package io.vamp.workflow_driver

import akka.actor.{ ActorRef, ActorRefFactory }
import akka.pattern.ask
import io.vamp.common.akka.ActorRefFactoryExecutionContextProvider
import io.vamp.container_driver.ContainerDriverActor.{ DeployWorkflow, GetWorkflow, UndeployWorkflow }
import io.vamp.container_driver.ContainerWorkflow
import io.vamp.model.artifact.{ DaemonSchedule, Workflow }
import io.vamp.workflow_driver.WorkflowDriverActor.Scheduled

import scala.concurrent.Future

abstract class DaemonWorkflowDriver(implicit override val actorRefFactory: ActorRefFactory) extends WorkflowDriver with ActorRefFactoryExecutionContextProvider {

  protected def driverActor: ActorRef

  override def request(replyTo: ActorRef, workflows: List[Workflow]): Unit = workflows.foreach { scheduled ⇒
    if (scheduled.schedule == DaemonSchedule) {
      driverActor ? GetWorkflow(scheduled) map {
        case ContainerWorkflow(_, Some(_)) ⇒ replyTo ! Scheduled(scheduled, Option(WorkflowInstance(scheduled.name)))
        case _                             ⇒ replyTo ! Scheduled(scheduled, None)
      }
    }
  }

  override def schedule(data: Any): PartialFunction[Workflow, Future[Any]] = {
    case workflow if workflow.schedule == DaemonSchedule ⇒
      driverActor ? GetWorkflow(workflow) map {
        case ContainerWorkflow(_, Some(_)) ⇒ driverActor ? DeployWorkflow(enrich(workflow), update = true)
        case _                             ⇒ driverActor ? DeployWorkflow(enrich(workflow), update = false)
      }
  }

  override def unschedule(): PartialFunction[Workflow, Future[Any]] = {
    case workflow if workflow.schedule == DaemonSchedule ⇒
      driverActor ? GetWorkflow(workflow) map {
        case ContainerWorkflow(_, Some(_)) ⇒ driverActor ? UndeployWorkflow(workflow)
        case _                             ⇒ Future.successful(true)
      }
  }
}
