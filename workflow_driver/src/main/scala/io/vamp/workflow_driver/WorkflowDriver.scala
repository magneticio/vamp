package io.vamp.workflow_driver

import io.vamp.model.workflow.ScheduledWorkflow
import io.vamp.persistence.kv.KeyValueStoreActor

import scala.concurrent.Future

case class WorkflowInstance(name: String)

object WorkflowDriver {

  def path(scheduledWorkflow: ScheduledWorkflow, workflow: Boolean = false) = {
    if (workflow) "scheduled-workflow" :: scheduledWorkflow.name :: "workflow" :: Nil else "scheduled-workflow" :: scheduledWorkflow.name :: Nil
  }

  def pathToString(scheduledWorkflow: ScheduledWorkflow) = KeyValueStoreActor.pathToString(path(scheduledWorkflow))
}

trait WorkflowDriver {

  def info: Future[Map[_, _]]

  def all(): Future[List[WorkflowInstance]]

  def schedule(data: Any): PartialFunction[ScheduledWorkflow, Future[Any]]

  def unschedule(): PartialFunction[ScheduledWorkflow, Future[Any]]
}
