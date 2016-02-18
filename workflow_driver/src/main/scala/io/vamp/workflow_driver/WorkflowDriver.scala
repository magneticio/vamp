package io.vamp.workflow_driver

import io.vamp.model.workflow.ScheduledWorkflow

import scala.concurrent.Future

case class WorkflowInstance(name: String)

trait WorkflowDriver {

  def info: Future[Any]

  def all(): Future[List[WorkflowInstance]]

  def schedule(workflow: ScheduledWorkflow, data: Any): Future[Any]

  def unschedule(workflow: ScheduledWorkflow): Future[Any]
}
