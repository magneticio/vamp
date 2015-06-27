package io.vamp.core.operation.workflow

import javax.script.Bindings

import akka.actor.{Actor, ActorLogging}
import io.vamp.common.akka.{ActorSupport, ExecutionContextProvider, FutureSupport}
import io.vamp.core.model.artifact.Deployment
import io.vamp.core.model.workflow._
import io.vamp.core.persistence.{ArtifactSupport, PersistenceActor}
import jdk.nashorn.api.scripting.NashornScriptEngineFactory

import scala.collection.Set
import scala.concurrent.Future
import scala.io.Source

trait WorkflowExecutor {
  this: Actor with ActorLogging with ArtifactSupport with ActorSupport with FutureSupport with ExecutionContextProvider =>

  private val urlPattern = "^(https?:\\/\\/.+)$".r

  def execute(scheduledWorkflow: ScheduledWorkflow, data: Any) = Future {
    try {
      log.info(s"Executing workflow: $scheduledWorkflow")
      val refreshed = artifactFor[ScheduledWorkflow](scheduledWorkflow.name)
      eval(refreshed, artifactFor[DefaultWorkflow](refreshed.workflow), data)
    } catch {
      case e: Throwable => log.error(s"Exception during execution of '${scheduledWorkflow.name}': ${e.getMessage}", e)
    }
  }

  private def eval(scheduledWorkflow: ScheduledWorkflow, workflow: DefaultWorkflow, data: Any) = {
    val source = mergeSource(workflow)
    val engine = createEngine()
    val bindings = initializeBindings(scheduledWorkflow, engine.createBindings, data)

    val result = engine.eval(source, bindings)
    log.info(s"Result of '${scheduledWorkflow.name}': $result")
    postEvaluation(scheduledWorkflow, bindings)
  }

  private def createEngine() = {
    val arguments = List("-doe", "-strict", "--no-java").toArray
    val classLoader = {
      val ccl = Thread.currentThread().getContextClassLoader
      if (ccl == null) classOf[NashornScriptEngineFactory].getClassLoader else ccl
    }
    new NashornScriptEngineFactory().getScriptEngine(arguments, classLoader)
  }

  private def mergeSource(workflow: DefaultWorkflow) = {
    workflow.`import`.map {
      case urlPattern(url) =>
        log.debug(s"Importing the external script: $url")
        Source.fromURL(url).mkString
      case reference =>
        log.debug(s"Importing the internal script: $reference")
        artifactFor[DefaultWorkflow](reference).script
    } :+ workflow.script mkString "\n"
  }

  private def initializeBindings(scheduledWorkflow: ScheduledWorkflow, bindings: Bindings, data: Any) = {
    addContextBindings(scheduledWorkflow, bindings)
    addScheduledWorkflowBindings(scheduledWorkflow, bindings, data)
    purgeBindings(bindings)
    bindings
  }

  private def addContextBindings(implicit scheduledWorkflow: ScheduledWorkflow, bindings: Bindings) = {
    bindings.put("log", new LoggerContext)
    bindings.put("time", new TimeContext)
    bindings.put("storage", new StorageContext)
    bindings.put("http", new HttpClientContext)
    bindings.put("events", new EventApiContext(actorRefFactory))
    bindings.put("vamp", new InfoContext(actorRefFactory))

    List("breeds", "blueprints", "slas", "scales", "escalations", "routings", "filters", "workflows", "scheduled-workflows").map { group =>
      bindings.put(group.replace('-', '_'), new ArtifactApiContext(group))
    }

    bindings.put("deployments", new DeploymentApiContext)
  }

  private def addScheduledWorkflowBindings(scheduledWorkflow: ScheduledWorkflow, bindings: Bindings, data: Any) = {
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
  }

  private def purgeBindings(implicit bindings: Bindings) = List("echo", "print", "readLine", "readFully", "quit", "exit", "load", "loadWithNewGlobal").foreach { attribute =>
    bindings.remove(attribute)
  }

  private def postEvaluation(scheduledWorkflow: ScheduledWorkflow, bindings: Bindings) = {
    bindings.get("storage") match {
      case storage: StorageContext =>
        actorFor(PersistenceActor) ! PersistenceActor.Update(scheduledWorkflow.copy(storage = storage.all()))
      case _ =>
    }
  }
}
