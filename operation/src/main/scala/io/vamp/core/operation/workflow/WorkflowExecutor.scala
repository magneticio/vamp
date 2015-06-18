package io.vamp.core.operation.workflow

import javax.script.ScriptEngineManager

import akka.actor.{Actor, ActorLogging}
import com.typesafe.scalalogging.Logger
import io.vamp.core.model.workflow.{DefaultWorkflow, ScheduledWorkflow}
import io.vamp.core.persistence.actor.ArtifactSupport
import org.slf4j.LoggerFactory

import scala.io.Source
import scala.language.postfixOps

trait WorkflowExecutor {
  this: Actor with ActorLogging with ArtifactSupport =>

  private val urlPattern = "^(https?:\\/\\/.+)$".r

  def execute: (ScheduledWorkflow => Unit) = { (scheduledWorkflow: ScheduledWorkflow) =>
    log.info(s"Executing workflow: $scheduledWorkflow")
    eval(scheduledWorkflow.name, artifactFor[DefaultWorkflow](scheduledWorkflow.workflow))
  }

  private def eval(name: String, workflow: DefaultWorkflow) = {
    val engine = new ScriptEngineManager().getEngineByName("nashorn")

    val bindings = engine.createBindings
    bindings.put("vamp", new ExecutionContext(name))

    val source = workflow.`import`.map {
      case urlPattern(url) => Source.fromURL(url).mkString
      case reference => artifactFor[DefaultWorkflow](reference).script
    } :+ workflow.script mkString "\n"

    engine.eval(source, bindings)
  }
}

class ExecutionContext(loggerName: String) extends LoggerContext {
  protected val logger = Logger(LoggerFactory.getLogger(loggerName))
}

trait LoggerContext {
  this: ExecutionContext =>

  def trace(any: Any) = logger.trace(messageOf(any))

  def debug(any: Any) = logger.debug(messageOf(any))

  def info(any: Any) = logger.info(messageOf(any))

  def warn(any: Any) = logger.warn(messageOf(any))

  def error(any: Any) = logger.error(messageOf(any))

  @inline private def messageOf(any: Any) = if (any != null) any.toString else ""
}

