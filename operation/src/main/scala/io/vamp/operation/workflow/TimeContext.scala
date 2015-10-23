package io.vamp.operation.workflow

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import io.vamp.model.workflow.ScheduledWorkflow

import scala.concurrent.ExecutionContext

class TimeContext(implicit scheduledWorkflow: ScheduledWorkflow, executionContext: ExecutionContext) extends ScriptingContext {

  def format(pattern: String) = now().format(DateTimeFormatter.ofPattern(pattern))

  def now() = OffsetDateTime.now()

  def epoch() = now().toEpochSecond

  def timestamp() = epoch()
}
