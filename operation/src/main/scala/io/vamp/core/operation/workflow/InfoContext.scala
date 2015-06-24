package io.vamp.core.operation.workflow

import io.vamp.common.akka.FutureSupport
import io.vamp.core.model.workflow.ScheduledWorkflow

import scala.concurrent.ExecutionContext

class InfoContext(implicit scheduledWorkflow: ScheduledWorkflow, executionContext: ExecutionContext) extends ScriptingContext with FutureSupport {

  def info() = {}
}
