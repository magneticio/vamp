package io.vamp.workflow_driver

import io.vamp.container_driver.marathon.MarathonDriverActor
import io.vamp.model.workflow.{ DaemonTrigger, ScheduledWorkflow }

import scala.concurrent.{ ExecutionContext, Future }

class MarathonWorkflowDriver(ec: ExecutionContext) extends WorkflowDriver {

  override def info: Future[Map[_, _]] = Future.successful(Map("marathon" -> Map("url" -> MarathonDriverActor.marathonUrl)))

  override def all(): Future[List[WorkflowInstance]] = Future.successful(Nil)

  override def schedule(data: Any): PartialFunction[ScheduledWorkflow, Future[Any]] = {
    case scheduledWorkflow if scheduledWorkflow.trigger == DaemonTrigger ⇒
      Future.successful(scheduledWorkflow)
  }

  override def unschedule(): PartialFunction[ScheduledWorkflow, Future[Any]] = {
    case scheduledWorkflow if scheduledWorkflow.trigger == DaemonTrigger ⇒
      Future.successful(scheduledWorkflow)
  }
}

