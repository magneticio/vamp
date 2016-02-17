package io.vamp.workflow_driver

import io.vamp.common.http.RestClient
import io.vamp.model.workflow.ScheduledWorkflow

import scala.concurrent.{ ExecutionContext, Future }

class ChronosWorkflowDriver(ec: ExecutionContext, url: String) extends WorkflowDriver {
  private implicit val executionContext = ec

  override def info: Future[Any] = RestClient.get[Any](s"$url/scheduler/jobs").map {
    _ ⇒ Map("type" -> "chronos", "url" -> url)
  }

  override def schedule(scheduledWorkflow: ScheduledWorkflow): Future[Any] = ???

  override def unschedule(scheduledWorkflow: ScheduledWorkflow): Future[Any] = ???

  override def all: Future[List[WorkflowInstance]] = ???
}
