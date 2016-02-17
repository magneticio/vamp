package io.vamp.workflow_driver

import io.vamp.model.workflow.ScheduledWorkflow

import scala.concurrent.{ ExecutionContext, Future }

class ChronosWorkflowDriver(ec: ExecutionContext) extends WorkflowDriver {

  override def info: Future[Any] = ???

  override def schedule(scheduledWorkflow: ScheduledWorkflow): Future[Any] = ???

  override def unschedule(scheduledWorkflow: ScheduledWorkflow): Future[Any] = ???

  override def all: Future[List[WorkflowInstance]] = ???
}
