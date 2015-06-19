package io.vamp.core.operation.workflow

import javax.script.Bindings

import akka.actor.{Actor, ActorLogging}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import io.vamp.common.akka.{ActorSupport, ExecutionContextProvider, FutureSupport}
import io.vamp.common.http.RestClient
import io.vamp.core.model.artifact.Deployment
import io.vamp.core.model.workflow._
import io.vamp.core.persistence.actor.{ArtifactSupport, PersistenceActor}
import jdk.nashorn.api.scripting.NashornScriptEngineFactory
import org.slf4j.LoggerFactory

import scala.collection.{Set, mutable}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
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
    val engine = new NashornScriptEngineFactory().getScriptEngine(List("-doe", "-strict", "--no-java", "--no-syntax-extensions").toArray: _*)
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
    bindings.put("http", new HttpClientContext(executionContext))

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

  def getOrElse(key: String, default: Any = null) = store.getOrElse(key, default)

  def remove(key: String) = store.remove(key).orNull

  def put(key: String, value: Any) = store.put(key, value).orNull

  def clear() = store.clear()
}

class HttpClientContext(ec: ExecutionContext) extends FutureSupport {

  import RestClient._

  implicit val executionContext = ec
  implicit lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.core.operation.workflow.http.timeout").seconds)

  private var body: Any = null
  private var url: Option[String] = None
  private var method: Option[Method.Value] = None
  private var headers: List[(String, String)] = Nil

  def set(name: String, value: String): HttpClientContext = {
    headers = headers :+ (name -> value)
    this
  }

  def send(body: Any): HttpClientContext = {
    this.body = body
    this
  }

  def get(url: String): HttpClientContext = methodUrl(Method.GET, url)

  def post(url: String, body: Any = null): HttpClientContext = methodUrl(Method.POST, url)

  def put(url: String, body: Any = null): HttpClientContext = methodUrl(Method.PUT, url)

  def delete(url: String): HttpClientContext = methodUrl(Method.DELETE, url)

  def string(): String = offload(call(asJson = false)) match {
    case None => ""
    case Some(response) => response.toString
  }

  def json() = offload(call(asJson = true)) match {
    case None => Map()
    case Some(response) => response
  }

  @inline private def methodUrl(method: Method.Value, url: String): HttpClientContext = {
    this.url = Some(url)
    this.method = Some(method)
    this
  }

  @inline private def call(asJson: Boolean): Future[Option[Any]] = (method, url) match {
    case (Some(m), Some(u)) => http(m, headers, u, body, asJson = asJson)
    case _ => throw new RuntimeException(s"HTTP: method or URL not specified.")
  }
}

