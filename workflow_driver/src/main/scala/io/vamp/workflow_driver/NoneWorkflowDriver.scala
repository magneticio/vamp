package io.vamp.workflow_driver

import io.vamp.model.workflow.ScheduledWorkflow

import scala.concurrent.Future

object NoneWorkflowDriver extends WorkflowDriver {

  override def info: Future[Map[_, _]] = Future.successful(Map())

  override def all(): Future[List[WorkflowInstance]] = Future.successful(Nil)

  override def schedule(data: Any): PartialFunction[ScheduledWorkflow, Future[Any]] = {
    case workflow ⇒ Future.successful(workflow)
  }

  override def unschedule(): PartialFunction[ScheduledWorkflow, Future[Any]] = {
    case workflow ⇒ Future.successful(workflow)
  }
}
