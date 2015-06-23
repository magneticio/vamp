package io.vamp.core.rest_api

import _root_.io.vamp.core.operation.workflow.{WorkflowConfiguration, WorkflowSchedulerActor}
import _root_.io.vamp.core.persistence.PersistenceActor
import akka.actor.Actor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.{ActorSupport, ExecutionContextProvider, FutureSupport}
import io.vamp.core.model.artifact._
import io.vamp.core.model.reader._
import io.vamp.core.model.workflow.{ScheduledWorkflow, TimeTrigger, Workflow}
import io.vamp.core.rest_api.notification.{InconsistentArtifactName, InvalidTimeTriggerError, RestApiNotificationProvider, UnexpectedArtifact}
import org.quartz.CronExpression

import scala.concurrent.Future
import scala.language.{existentials, postfixOps}
import scala.reflect._

trait RestApiController extends RestApiNotificationProvider with ActorSupport with FutureSupport {
  this: Actor with ExecutionContextProvider =>

  def allArtifacts(artifact: String, page: Int, perPage: Int)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.all(page, perPage)
    case None => error(UnexpectedArtifact(artifact))
  }

  def createArtifact(artifact: String, content: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.create(content, validateOnly)
    case None => error(UnexpectedArtifact(artifact))
  }

  def readArtifact(artifact: String, name: String)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.read(name)
    case None => error(UnexpectedArtifact(artifact))
  }

  def updateArtifact(artifact: String, name: String, content: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.update(name, content, validateOnly)
    case None => error(UnexpectedArtifact(artifact))
  }

  def deleteArtifact(artifact: String, name: String, content: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.delete(name, validateOnly)
    case None => error(UnexpectedArtifact(artifact))
  }

  private val workflowMapping: Map[String, Handler] = if (WorkflowConfiguration.enabled) {
    Map() + ("workflows" -> new PersistenceHandler[Workflow](WorkflowReader)) + ("scheduled-workflows" -> new ScheduledWorkflowHandler())
  } else Map()

  private val mapping: Map[String, Handler] = Map() +
    ("breeds" -> new PersistenceHandler[Breed](BreedReader)) +
    ("blueprints" -> new PersistenceHandler[Blueprint](BlueprintReader)) +
    ("slas" -> new PersistenceHandler[Sla](SlaReader)) +
    ("scales" -> new PersistenceHandler[Scale](ScaleReader)) +
    ("escalations" -> new PersistenceHandler[Escalation](EscalationReader)) +
    ("routings" -> new PersistenceHandler[Routing](RoutingReader)) +
    ("filters" -> new PersistenceHandler[Filter](FilterReader)) +
    ("deployments" -> new Handler()) ++ workflowMapping

  class Handler {

    def all(page: Int, perPage: Int)(implicit timeout: Timeout): Future[Any] = Future(Nil)

    def create(source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = Future(error(UnexpectedArtifact(source)))

    def read(name: String)(implicit timeout: Timeout): Future[Any] = Future(None)

    def update(name: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = Future(error(UnexpectedArtifact(source)))

    def delete(name: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = Future(None)
  }

  class PersistenceHandler[T <: Artifact : ClassTag](unmarshaller: YamlReader[T]) extends Handler {

    val `type` = classTag[T].runtimeClass.asInstanceOf[Class[_ <: Artifact]]

    override def all(page: Int, perPage: Int)(implicit timeout: Timeout) =
      actorFor(PersistenceActor) ? PersistenceActor.AllPaginated(`type`, page, perPage)

    override def create(source: String, validateOnly: Boolean)(implicit timeout: Timeout) = {
      val artifact = (unmarshal andThen validate)(source)
      if (validateOnly) Future(artifact) else actorFor(PersistenceActor) ? PersistenceActor.Create(artifact, Some(source))
    }

    override def read(name: String)(implicit timeout: Timeout) = actorFor(PersistenceActor) ? PersistenceActor.Read(name, `type`)

    override def update(name: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout) = {
      val artifact = (unmarshal andThen validate)(source)
      if (name != artifact.name)
        error(InconsistentArtifactName(name, artifact))

      if (validateOnly) Future(artifact) else actorFor(PersistenceActor) ? PersistenceActor.Update(artifact, Some(source))
    }

    override def delete(name: String, validateOnly: Boolean)(implicit timeout: Timeout) =
      if (validateOnly) Future(None) else actorFor(PersistenceActor) ? PersistenceActor.Delete(name, `type`)

    protected def unmarshal: (String => T) = { (source: String) => unmarshaller.read(source) }

    protected def validate: (T => T) = { (artifact: T) => artifact }
  }

  class ScheduledWorkflowHandler extends PersistenceHandler[ScheduledWorkflow](ScheduledWorkflowReader) {

    override def create(source: String, validateOnly: Boolean)(implicit timeout: Timeout) = super.create(source, validateOnly).map {
      case workflow: ScheduledWorkflow =>
        actorFor(WorkflowSchedulerActor) ! WorkflowSchedulerActor.Schedule(workflow)
        workflow
      case any => any
    }

    override def update(name: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout) = super.update(name, source, validateOnly).map {
      case workflow: ScheduledWorkflow =>
        actorFor(WorkflowSchedulerActor) ! WorkflowSchedulerActor.Schedule(workflow)
        workflow
      case any => any
    }

    override def delete(name: String, validateOnly: Boolean)(implicit timeout: Timeout) = super.delete(name, validateOnly).map {
      case workflow: ScheduledWorkflow =>
        actorFor(WorkflowSchedulerActor) ! WorkflowSchedulerActor.Unschedule(workflow)
        workflow
      case any => any
    }

    override protected def validate: (ScheduledWorkflow => ScheduledWorkflow) = { (scheduledWorkflow: ScheduledWorkflow) =>
      scheduledWorkflow.trigger match {
        case TimeTrigger(pattern) => if (!CronExpression.isValidExpression(pattern)) error(InvalidTimeTriggerError(pattern))
        case _ =>
      }

      scheduledWorkflow
    }
  }

}
