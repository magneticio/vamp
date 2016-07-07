package io.vamp.workflow_driver

import akka.actor.ActorRef
import io.vamp.common.config.Config
import io.vamp.model.workflow.ScheduledWorkflow
import io.vamp.persistence.kv.KeyValueStoreActor

import scala.concurrent.Future

case class WorkflowInstance(name: String)

object WorkflowDriver {

  val config = Config.config("vamp.workflow-driver")

  val vampUrl = config.string("vamp-url")

  val network = config.string("network")

  def path(scheduledWorkflow: ScheduledWorkflow, workflow: Boolean = false) = {
    if (workflow) "scheduled-workflow" :: scheduledWorkflow.name :: "workflow" :: Nil else "scheduled-workflow" :: scheduledWorkflow.name :: Nil
  }

  def pathToString(scheduledWorkflow: ScheduledWorkflow) = KeyValueStoreActor.pathToString(path(scheduledWorkflow))
}

trait WorkflowDriver {

  def info: Future[Map[_, _]]

  def request(replyTo: ActorRef, scheduledWorkflows: List[ScheduledWorkflow]): Unit

  def schedule(data: Any): PartialFunction[ScheduledWorkflow, Future[Any]]

  def unschedule(): PartialFunction[ScheduledWorkflow, Future[Any]]
}
