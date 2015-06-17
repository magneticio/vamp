package io.vamp.core.operation.workflow

import akka.actor.{Actor, ActorLogging}
import io.vamp.core.model.workflow.ScheduledWorkflow


trait WorkflowExecutor {
  this: Actor with ActorLogging =>

  def execute: (ScheduledWorkflow => Unit) = { (scheduledWorkflow: ScheduledWorkflow) =>
    log.info(s"running: $scheduledWorkflow")
  }
}
