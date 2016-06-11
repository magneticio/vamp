package io.vamp.workflow_driver

import akka.actor.ActorRef
import io.vamp.model.workflow.ScheduledWorkflow

import scala.concurrent.Future

object NoneWorkflowDriver extends WorkflowDriver {

  override def info: Future[Map[_, _]] = Future.successful(Map())

  override def request(replyTo: ActorRef, scheduledWorkflows: List[ScheduledWorkflow]): Unit = {}

  override def schedule(data: Any): PartialFunction[ScheduledWorkflow, Future[Any]] = {
    case workflow ⇒ Future.successful(workflow)
  }

  override def unschedule(): PartialFunction[ScheduledWorkflow, Future[Any]] = {
    case workflow ⇒ Future.successful(workflow)
  }
}
