package io.vamp.workflow_driver

import akka.actor.ActorRef
import io.vamp.model.workflow.Workflow

import scala.concurrent.Future

object NoneWorkflowDriver extends WorkflowDriver {

  override def info: Future[Map[_, _]] = Future.successful(Map())

  override def request(replyTo: ActorRef, workflows: List[Workflow]): Unit = {}

  override def schedule(data: Any): PartialFunction[Workflow, Future[Any]] = {
    case workflow ⇒ Future.successful(workflow)
  }

  override def unschedule(): PartialFunction[Workflow, Future[Any]] = {
    case workflow ⇒ Future.successful(workflow)
  }
}
