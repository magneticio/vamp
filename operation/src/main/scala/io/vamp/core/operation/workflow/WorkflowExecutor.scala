package io.vamp.core.operation.workflow

import javax.script.Bindings

import akka.actor.{Actor, ActorLogging}
import io.vamp.common.akka.{ActorSupport, ExecutionContextProvider, FutureSupport}
import io.vamp.core.model.artifact.Deployment
import io.vamp.core.model.workflow._
import io.vamp.core.persistence.{PersistenceActor, ArtifactSupport}
import io.vamp.core.persistence.PersistenceActor
import jdk.nashorn.api.scripting.NashornScriptEngineFactory

import scala.collection.Set
import scala.concurrent.Future
import scala.io.Source

trait WorkflowExecutor {
  this: Actor with ActorLogging with ArtifactSupport with ActorSupport with FutureSupport with ExecutionContextProvider =>

  private val urlPattern = "^(https?:\\/\\/.+)$".r

  def execute(scheduledWorkflow: ScheduledWorkflow, data: Any) = {
    log.info(s"Executing workflow: $scheduledWorkflow")
    eval(scheduledWorkflow, artifactFor[DefaultWorkflow](scheduledWorkflow.workflow), data)
  }

  private def eval(scheduledWorkflow: ScheduledWorkflow, workflow: DefaultWorkflow, data: Any) = Future {
    val source = mergeSource(workflow)
    val engine = createEngine()
    val bindings = initializeBindings(scheduledWorkflow, engine.createBindings, data)

    engine.eval(source, bindings)
    postEvaluation(scheduledWorkflow, bindings)
  }

  private def createEngine() = {
    val arguments = List("-doe", "-strict", "--no-java", "--no-syntax-extensions").toArray
    val classLoader = {
      val ccl = Thread.currentThread().getContextClassLoader
      if (ccl == null) classOf[NashornScriptEngineFactory].getClassLoader else ccl
    }
    new NashornScriptEngineFactory().getScriptEngine(arguments, classLoader)
  }

  private def mergeSource(workflow: DefaultWorkflow) = {
    workflow.`import`.map {
      case urlPattern(url) => Source.fromURL(url).mkString
      case reference => artifactFor[DefaultWorkflow](reference).script
    } :+ workflow.script mkString "\n"
  }

  private def initializeBindings(scheduledWorkflow: ScheduledWorkflow, bindings: Bindings, data: Any) = {
    // reload scheduled workflow from the persistence
    val sw = artifactFor[ScheduledWorkflow](scheduledWorkflow.name)
    bindings.put("log", new LoggerContext(sw))
    bindings.put("time", new TimeContext(sw))
    bindings.put("storage", new StorageContext(sw))
    bindings.put("http", new HttpClientContext(sw))
    bindings.put("events", new EventApiContext(sw))

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

    List("echo", "print", "readLine", "readFully", "quit", "exit", "load", "loadWithNewGlobal").foreach { attribute =>
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
