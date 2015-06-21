package io.vamp.core.operation.workflow

import com.typesafe.scalalogging.Logger
import io.vamp.core.model.workflow.ScheduledWorkflow
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext

abstract class ScriptingContext(scheduledWorkflow: ScheduledWorkflow)(implicit executionContext: ExecutionContext) {

  protected val logger = Logger(LoggerFactory.getLogger(scheduledWorkflow.name))
}
