package io.vamp.core.operation.workflow

import javax.script.Bindings

import akka.actor.{Actor, ActorLogging}
import io.vamp.common.akka.{ActorSystemProvider, ExecutionContextProvider, IoC}
import io.vamp.core.model.artifact.Deployment
import io.vamp.core.model.workflow._
import io.vamp.core.persistence.{ArtifactSupport, PersistenceActor}
import jdk.nashorn.api.scripting.NashornScriptEngineFactory

import scala.collection.Set
import scala.concurrent.Future
import scala.io.Source

trait WorkflowExecutor {
  this: Actor with ActorLogging with ActorSystemProvider with ArtifactSupport with ExecutionContextProvider =>

  private val urlPattern = "^(https?:\\/\\/.+)$".r

  def execute(scheduledWorkflow: ScheduledWorkflow, data: Any): Future[_] = {
    log.info(s"Executing workflow: $scheduledWorkflow")
    for {
      refreshed <- artifactFor[ScheduledWorkflow](scheduledWorkflow.name)
      workflow <- artifactFor[DefaultWorkflow](refreshed.workflow)
    } yield eval(refreshed, workflow, data)
  }

  private def eval(scheduledWorkflow: ScheduledWorkflow, workflow: DefaultWorkflow, data: Any) = mergeSource(workflow) map {
    case source =>
      try {
        val engine = createEngine()
        val bindings = initializeBindings(scheduledWorkflow, engine.createBindings, data)

        val result = engine.eval(source, bindings)
        log.info(s"Result of '${scheduledWorkflow.name}': $result")
        postEvaluation(scheduledWorkflow, bindings)
      } catch {
        case e: Throwable => log.error(s"Exception during execution of '${scheduledWorkflow.name}': ${e.getMessage}", e)
      }
  }

  private def createEngine() = {
    val arguments = List("-doe", "-strict", "--no-java").toArray
    val classLoader = {
      val ccl = Thread.currentThread().getContextClassLoader
      if (ccl == null) classOf[NashornScriptEngineFactory].getClassLoader else ccl
    }
    new NashornScriptEngineFactory().getScriptEngine(arguments, classLoader)
  }

  private def mergeSource(workflow: DefaultWorkflow): Future[String] = Future.sequence(workflow.`import`.map {
    case urlPattern(url) =>
      log.debug(s"Importing the external script: $url")
      Future(Source.fromURL(url).mkString)
    case reference =>
      log.debug(s"Importing the internal script: $reference")
      artifactFor[DefaultWorkflow](reference).map(_.script)
  } :+ Future(workflow.script)).map(_.mkString("\n"))

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
    bindings.put("events", new EventApiContext(actorSystem))
    bindings.put("vamp", new InfoContext(actorSystem))

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
        IoC.actorFor[PersistenceActor] ! PersistenceActor.Update(scheduledWorkflow.copy(storage = storage.all()))
      case _ =>
    }
  }
}
