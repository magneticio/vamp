package io.vamp.workflow_driver

import io.vamp.model.workflow.ScheduledWorkflow

import scala.concurrent.Future

case class WorkflowInstance()

trait WorkflowDriver {

  def info: Future[Any]

  def all: Future[List[WorkflowInstance]]

  def schedule(scheduledWorkflow: ScheduledWorkflow): Future[Any]

  def unschedule(scheduledWorkflow: ScheduledWorkflow): Future[Any]
}
