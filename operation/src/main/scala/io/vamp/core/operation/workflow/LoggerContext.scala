package io.vamp.core.operation.workflow

import io.vamp.core.model.workflow.ScheduledWorkflow

import scala.concurrent.ExecutionContext

class LoggerContext(implicit scheduledWorkflow: ScheduledWorkflow, executionContext: ExecutionContext) extends ScriptingContext {

  def trace(any: Any) = logger.trace(messageOf(any))

  def debug(any: Any) = logger.debug(messageOf(any))

  def info(any: Any) = logger.info(messageOf(any))

  def warn(any: Any) = logger.warn(messageOf(any))

  def error(any: Any) = logger.error(messageOf(any))

  def log(any: Any) = info(any)

  private def messageOf(any: Any) = if (any != null) any.toString else ""
}
