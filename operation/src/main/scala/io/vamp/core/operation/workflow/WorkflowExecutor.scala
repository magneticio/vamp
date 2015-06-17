package io.vamp.core.operation.workflow

import javax.script.ScriptEngineManager

import akka.actor.{Actor, ActorLogging}
import com.typesafe.scalalogging.Logger
import io.vamp.core.model.workflow.{DefaultWorkflow, ScheduledWorkflow}
import io.vamp.core.persistence.actor.ArtifactSupport
import org.slf4j.LoggerFactory


trait WorkflowExecutor {
  this: Actor with ActorLogging with ArtifactSupport =>

  def execute: (ScheduledWorkflow => Unit) = { (scheduledWorkflow: ScheduledWorkflow) =>
    log.info(s"Executing workflow: $scheduledWorkflow")

    val workflow = artifactFor[DefaultWorkflow](scheduledWorkflow.workflow)
    val engine = new ScriptEngineManager().getEngineByName("JavaScript")

    engine.put("vamp", new ExecutionContext(scheduledWorkflow.name))
    engine.eval(workflow.script)
  }
}

class ExecutionContext(loggerName: String) extends LoggerContext {
  protected val logger = Logger(LoggerFactory.getLogger(loggerName))
}

trait LoggerContext {
  this: ExecutionContext =>

  def trace(message: String) = logger.trace(message)

  def debug(message: String) = logger.debug(message)

  def info(message: String) = logger.info(message)

  def warn(message: String) = logger.warn(message)

  def error(message: String) = logger.error(message)
}

