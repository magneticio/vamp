package io.vamp.core.operation.workflow

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import io.vamp.common.akka.FutureSupport
import io.vamp.core.model.workflow.ScheduledWorkflow

import scala.concurrent.ExecutionContext

class TimeContext(scheduledWorkflow: ScheduledWorkflow)(implicit executionContext: ExecutionContext) extends ScriptingContext(scheduledWorkflow) with FutureSupport {

  def format(pattern: String) = now().format(DateTimeFormatter.ofPattern(pattern))

  def now() = OffsetDateTime.now()

  def epoch() = now().toEpochSecond

  def timestamp() = epoch()
}
