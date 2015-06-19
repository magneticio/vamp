package io.vamp.core.operation.workflow

import javax.script.Bindings

import akka.actor.{Actor, ActorLogging}
import com.typesafe.scalalogging.Logger
import io.vamp.common.akka.{ActorSupport, ExecutionContextProvider, FutureSupport}
import io.vamp.core.model.artifact.Deployment
import io.vamp.core.model.workflow._
import io.vamp.core.persistence.actor.{ArtifactSupport, PersistenceActor}
import jdk.nashorn.api.scripting.NashornScriptEngineFactory
import org.slf4j.LoggerFactory

import scala.collection.{Set, mutable}
import scala.concurrent.Future
import scala.io.Source
import scala.language.postfixOps

trait WorkflowExecutor {
  this: Actor with ActorLogging with ArtifactSupport with ActorSupport with FutureSupport with ExecutionContextProvider =>

  private val urlPattern = "^(https?:\\/\\/.+)$".r

  def execute(scheduledWorkflow: ScheduledWorkflow, data: Any) = {
    log.info(s"Executing workflow: $scheduledWorkflow")
    eval(scheduledWorkflow, artifactFor[DefaultWorkflow](scheduledWorkflow.workflow), data)
  }

  private def eval(scheduledWorkflow: ScheduledWorkflow, workflow: DefaultWorkflow, data: Any) = Future {
    val source = mergeSource(workflow)
    val engine = new NashornScriptEngineFactory().getScriptEngine("-doe", "-strict", "--no-java", "--no-syntax-extensions")
    val bindings = initializeBindings(scheduledWorkflow, engine.createBindings, data)

    engine.eval(source, bindings)
    postEvaluation(scheduledWorkflow, bindings)
  }

  private def mergeSource(workflow: DefaultWorkflow) = {
    workflow.`import`.map {
      case urlPattern(url) => Source.fromURL(url).mkString
      case reference => artifactFor[DefaultWorkflow](reference).script
    } :+ workflow.script mkString "\n"
  }

  private def initializeBindings(scheduledWorkflow: ScheduledWorkflow, bindings: Bindings, data: Any) = {
    bindings.put("log", new LoggerContext(scheduledWorkflow.name))
    bindings.put("storage", new StorageContext(artifactFor[ScheduledWorkflow](scheduledWorkflow.name).storage))

    def tags() = data match {
      case ref: AnyRef if ref.isInstanceOf[Set[_]] => bindings.put("tags", ref.asInstanceOf[Set[String]].toArray)
    }

    scheduledWorkflow.trigger match {
      case TimeTrigger(_) => bindings.put("timestamp", data)
      case EventTrigger(_) => tags()
      case DeploymentTrigger(deployment) =>
        tags()
        bindings.put("deployment", artifactFor[Deployment](deployment))
      case _ => log.debug(s"No execution data for: ${scheduledWorkflow.name}")
    }

    List("quit", "exit", "load", "loadWithNewGlobal").foreach { attribute =>
      bindings.remove(attribute)
    }

    bindings
  }

  private def postEvaluation(scheduledWorkflow: ScheduledWorkflow, bindings: Bindings) = {
    bindings.get("storage") match {
      case storage: StorageContext =>
        actorFor(PersistenceActor) ! PersistenceActor.Update(scheduledWorkflow.copy(storage = storage.all()))
      case _ =>
    }
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

class StorageContext(storage: Map[String, Any]) {

  private val store = mutable.Map[String, Any]() ++ storage

  def all() = store.toMap

  def get(key: String) = store.get(key).orNull

  def remove(key: String) = store.remove(key).orNull

  def put(key: String, value: Any) = store.put(key, value).orNull

  def clear() = store.clear()
}

