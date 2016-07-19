package io.vamp.workflow_driver

import akka.actor.ActorRef
import io.vamp.common.config.Config
import io.vamp.model.workflow.Workflow
import io.vamp.persistence.kv.KeyValueStoreActor

import scala.concurrent.Future

case class WorkflowInstance(name: String)

object WorkflowDriver {

  val config = Config.config("vamp.workflow-driver")

  val vampUrl = config.string("vamp-url")

  val network = config.string("network")

  def path(workflow: Workflow) = "workflow" :: workflow.name :: Nil

  def pathToString(scheduledWorkflow: Workflow) = KeyValueStoreActor.pathToString(path(scheduledWorkflow))
}

trait WorkflowDriver {

  def info: Future[Map[_, _]]

  def request(replyTo: ActorRef, scheduledWorkflows: List[Workflow]): Unit

  def schedule(data: Any): PartialFunction[Workflow, Future[Any]]

  def unschedule(): PartialFunction[Workflow, Future[Any]]
}
