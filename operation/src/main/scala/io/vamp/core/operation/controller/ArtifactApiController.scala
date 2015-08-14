package io.vamp.core.operation.controller

import _root_.io.vamp.common.akka.{ActorSupport, ExecutionContextProvider, FutureSupport}
import _root_.io.vamp.common.notification.NotificationProvider
import _root_.io.vamp.core.model.workflow.{DefaultWorkflow, ScheduledWorkflow, TimeTrigger, Workflow}
import _root_.io.vamp.core.operation.notification.{InconsistentArtifactName, InvalidTimeTriggerError, MissingRequiredVariableError, UnexpectedArtifact}
import _root_.io.vamp.core.persistence.{ArtifactSupport, PersistenceActor}
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.core.model.artifact._
import io.vamp.core.model.reader._
import io.vamp.core.operation.workflow.{WorkflowConfiguration, WorkflowSchedulerActor}
import org.quartz.CronExpression

import scala.concurrent.Future
import scala.language.{existentials, postfixOps}
import scala.reflect._

trait ArtifactApiController extends ArtifactSupport {
  this: ActorSupport with FutureSupport with ExecutionContextProvider with NotificationProvider =>

  def allArtifacts(artifact: String, expandReferences: Boolean, onlyReferences: Boolean)(page: Int, perPage: Int)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.all(page, perPage, expandReferences, onlyReferences)
    case None => throwException(UnexpectedArtifact(artifact))
  }

  def createArtifact(artifact: String, content: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.create(content, validateOnly)
    case None => throwException(UnexpectedArtifact(artifact))
  }

  def readArtifact(artifact: String, name: String, expandReferences: Boolean, onlyReferences: Boolean)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.read(name, expandReferences, onlyReferences)
    case None => throwException(UnexpectedArtifact(artifact))
  }

  def updateArtifact(artifact: String, name: String, content: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.update(name, content, validateOnly)
    case None => throwException(UnexpectedArtifact(artifact))
  }

  def deleteArtifact(artifact: String, name: String, content: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) => controller.delete(name, validateOnly)
    case None => throwException(UnexpectedArtifact(artifact))
  }

  private val mapping: Map[String, Handler] = Map() +
    ("breeds" -> new PersistenceHandler[Breed](BreedReader)) +
    ("blueprints" -> new PersistenceHandler[Blueprint](BlueprintReader)) +
    ("slas" -> new PersistenceHandler[Sla](SlaReader)) +
    ("scales" -> new PersistenceHandler[Scale](ScaleReader)) +
    ("escalations" -> new PersistenceHandler[Escalation](EscalationReader)) +
    ("routings" -> new PersistenceHandler[Routing](RoutingReader)) +
    ("filters" -> new PersistenceHandler[Filter](FilterReader)) +
    // workaround for None response.
    ("deployments" -> new Handler()) ++
    // workflow handlers
    (if (WorkflowConfiguration.enabled) {
      Map() + ("workflows" -> new PersistenceHandler[Workflow](WorkflowReader)) + ("scheduled-workflows" -> new ScheduledWorkflowHandler())
    } else Map())

  class Handler {

    def all(page: Int, perPage: Int, expandReferences: Boolean, onlyReferences: Boolean)(implicit timeout: Timeout): Future[Any] = Future(Nil)

    def create(source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = throwException(UnexpectedArtifact(source))

    def read(name: String, expandReferences: Boolean, onlyReferences: Boolean)(implicit timeout: Timeout): Future[Any] = Future(None)

    def update(name: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = throwException(UnexpectedArtifact(source))

    def delete(name: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = Future(None)
  }

  class PersistenceHandler[T <: Artifact : ClassTag](unmarshaller: YamlReader[T]) extends Handler {

    val `type` = classTag[T].runtimeClass.asInstanceOf[Class[_ <: Artifact]]

    override def all(page: Int, perPage: Int, expandReferences: Boolean, onlyReferences: Boolean)(implicit timeout: Timeout) =
      actorFor(PersistenceActor) ? PersistenceActor.AllPaginated(`type`, page, perPage, expandReferences, onlyReferences)

    override def create(source: String, validateOnly: Boolean)(implicit timeout: Timeout) = {
      val artifact = (unmarshal andThen validate)(source)
      if (validateOnly) Future(artifact) else actorFor(PersistenceActor) ? PersistenceActor.Create(artifact, Some(source))
    }

    override def read(name: String, expandReferences: Boolean, onlyReferences: Boolean)(implicit timeout: Timeout) = actorFor(PersistenceActor) ? PersistenceActor.Read(name, `type`, expandReferences, onlyReferences)

    override def update(name: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout) = {
      val artifact = (unmarshal andThen validate)(source)
      if (name != artifact.name)
        throwException(InconsistentArtifactName(name, artifact))

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
        case TimeTrigger(pattern) => if (!CronExpression.isValidExpression(pattern)) throwException(InvalidTimeTriggerError(pattern))
        case _ =>
      }

      artifactFor[DefaultWorkflow](scheduledWorkflow.workflow).requires.find(required => scheduledWorkflow.storage.get(required).isEmpty) match {
        case Some(required) => throwException(MissingRequiredVariableError(required))
        case _ =>
      }

      scheduledWorkflow
    }
  }

}
