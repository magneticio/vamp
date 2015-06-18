package io.vamp.core.operation.workflow

import javax.script.{Bindings, ScriptEngineManager}

import akka.actor.{Actor, ActorLogging}
import com.typesafe.scalalogging.Logger
import io.vamp.core.model.artifact.Deployment
import io.vamp.core.model.workflow._
import io.vamp.core.persistence.actor.ArtifactSupport
import org.slf4j.LoggerFactory

import scala.collection.Set
import scala.io.Source
import scala.language.postfixOps

trait WorkflowExecutor {
  this: Actor with ActorLogging with ArtifactSupport =>

  private val urlPattern = "^(https?:\\/\\/.+)$".r

  def execute: (ScheduledWorkflow, Any) => Unit = { (scheduledWorkflow: ScheduledWorkflow, data: Any) =>
    log.info(s"Executing workflow: $scheduledWorkflow")
    eval(scheduledWorkflow, artifactFor[DefaultWorkflow](scheduledWorkflow.workflow), data)
  }

  private def eval(scheduledWorkflow: ScheduledWorkflow, workflow: DefaultWorkflow, data: Any) = {
    val engine = new ScriptEngineManager().getEngineByName("nashorn")

    val source = workflow.`import`.map {
      case urlPattern(url) => Source.fromURL(url).mkString
      case reference => artifactFor[DefaultWorkflow](reference).script
    } :+ workflow.script mkString "\n"

    engine.eval(source, bindings(scheduledWorkflow, engine.createBindings, data))
  }

  private def bindings(scheduledWorkflow: ScheduledWorkflow, bindings: Bindings, data: Any) = {
    bindings.put("log", new LoggerContext(scheduledWorkflow.name))

    def tags() = if (data.isInstanceOf[Set[_]]) bindings.put("tags", data.asInstanceOf[Set[_]].toArray)

    scheduledWorkflow.trigger match {
      case TimeTrigger(_) => bindings.put("timestamp", data)

      case EventTrigger(_) => tags()

      case DeploymentTrigger(deployment) =>
        tags()
        bindings.put("deployment", artifactFor[Deployment](deployment))

      case _ => log.debug(s"No execute data for: ${scheduledWorkflow.name}")
    }

    bindings
  }
}

class LoggerContext(name: String) {

  private val logger = Logger(LoggerFactory.getLogger(name))

  def trace(any: Any) = logger.trace(messageOf(any))

  def debug(any: Any) = logger.debug(messageOf(any))

  def info(any: Any) = logger.info(messageOf(any))

  def warn(any: Any) = logger.warn(messageOf(any))

  def error(any: Any) = logger.error(messageOf(any))

  def log(any: Any) = info(any)

  @inline private def messageOf(any: Any) = if (any != null) any.toString else ""
}

