package io.vamp.operation.controller

import _root_.io.vamp.common.notification.NotificationProvider
import _root_.io.vamp.operation.gateway.GatewayActor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.IoC._
import io.vamp.common.akka.{ActorSystemProvider, ExecutionContextProvider}
import io.vamp.model.artifact._
import io.vamp.model.reader._
import io.vamp.model.workflow.{DefaultWorkflow, ScheduledWorkflow, TimeTrigger, Workflow}
import io.vamp.operation.notification.{InconsistentArtifactName, InvalidTimeTriggerError, MissingRequiredVariableError, UnexpectedArtifact}
import io.vamp.operation.workflow.WorkflowSchedulerActor
import io.vamp.persistence.db.{ArtifactResponseEnvelope, ArtifactSupport, PersistenceActor}
import io.vamp.persistence.notification.PersistenceOperationFailure
import org.quartz.CronExpression

import scala.concurrent.Future
import scala.language.{existentials, postfixOps}
import scala.reflect._

trait ArtifactApiController extends ArtifactSupport {
  this: ExecutionContextProvider with NotificationProvider with ActorSystemProvider ⇒

  def background(artifact: String): Boolean = mapping.get(artifact).exists(_.background)

  def allArtifacts(artifact: String, expandReferences: Boolean, onlyReferences: Boolean)(page: Int, perPage: Int)(implicit timeout: Timeout): Future[ArtifactResponseEnvelope] = mapping.get(artifact) match {
    case Some(controller) ⇒ controller.all(page, perPage, expandReferences, onlyReferences)
    case None ⇒ throwException(UnexpectedArtifact(artifact))
  }

  def createArtifact(artifact: String, content: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) ⇒ controller.create(content, validateOnly)
    case None ⇒ throwException(UnexpectedArtifact(artifact))
  }

  def readArtifact(artifact: String, name: String, expandReferences: Boolean, onlyReferences: Boolean)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) ⇒ controller.read(name, expandReferences, onlyReferences)
    case None ⇒ throwException(UnexpectedArtifact(artifact))
  }

  def updateArtifact(artifact: String, name: String, content: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) ⇒ controller.update(name, content, validateOnly)
    case None ⇒ throwException(UnexpectedArtifact(artifact))
  }

  def deleteArtifact(artifact: String, name: String, content: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = mapping.get(artifact) match {
    case Some(controller) ⇒ controller.delete(name, validateOnly)
    case None ⇒ throwException(UnexpectedArtifact(artifact))
  }

  private val mapping: Map[String, Handler] = Map() +
    ("breeds" -> new PersistenceHandler[Breed](BreedReader)) +
    ("blueprints" -> new PersistenceHandler[Blueprint](BlueprintReader)) +
    ("slas" -> new PersistenceHandler[Sla](SlaReader)) +
    ("scales" -> new PersistenceHandler[Scale](ScaleReader)) +
    ("escalations" -> new PersistenceHandler[Escalation](EscalationReader)) +
    ("routes" -> new PersistenceHandler[Route](RouteReader)) +
    ("filters" -> new PersistenceHandler[Filter](FilterReader)) +
    ("rewrites" -> new PersistenceHandler[Rewrite](RewriteReader)) +
    ("workflows" -> new PersistenceHandler[Workflow](WorkflowReader)) +
    ("scheduled-workflows" -> new ScheduledWorkflowHandler()) +
    ("gateways" -> new GatewayHandler() {
      override def background = true
    }) +
    // workaround for None response.
    ("deployments" -> new Handler())

  class Handler {

    def all(page: Int, perPage: Int, expandReferences: Boolean, onlyReferences: Boolean)(implicit timeout: Timeout): Future[ArtifactResponseEnvelope] = Future(ArtifactResponseEnvelope(Nil, 0, 1, ArtifactResponseEnvelope.maxPerPage))

    def create(source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = throwException(UnexpectedArtifact(source))

    def read(name: String, expandReferences: Boolean, onlyReferences: Boolean)(implicit timeout: Timeout): Future[Any] = Future(None)

    def update(name: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = throwException(UnexpectedArtifact(source))

    def delete(name: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = Future(None)

    def background: Boolean = false
  }

  class PersistenceHandler[T <: Artifact : ClassTag](unmarshaller: YamlReader[T]) extends Handler {

    val `type` = classTag[T].runtimeClass.asInstanceOf[Class[_ <: Artifact]]

    override def all(page: Int, perPage: Int, expandReferences: Boolean, onlyReferences: Boolean)(implicit timeout: Timeout) = {
      actorFor[PersistenceActor] ? PersistenceActor.All(`type`, page, perPage, expandReferences, onlyReferences) map {
        case envelope: ArtifactResponseEnvelope ⇒ envelope
        case other ⇒ throwException(PersistenceOperationFailure(other))
      }
    }

    override def create(source: String, validateOnly: Boolean)(implicit timeout: Timeout) = {
      (unmarshal andThen validate)(source) flatMap {
        case artifact ⇒ if (validateOnly) Future(artifact) else actorFor[PersistenceActor] ? PersistenceActor.Create(artifact, Some(source))
      }
    }

    override def read(name: String, expandReferences: Boolean, onlyReferences: Boolean)(implicit timeout: Timeout) = {
      actorFor[PersistenceActor] ? PersistenceActor.Read(name, `type`, expandReferences, onlyReferences)
    }

    override def update(name: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout) = {
      (unmarshal andThen validate)(source) flatMap {
        case artifact ⇒
          if (name != artifact.name)
            throwException(InconsistentArtifactName(name, artifact))

          if (validateOnly) Future(artifact) else actorFor[PersistenceActor] ? PersistenceActor.Update(artifact, Some(source))
      }
    }

    override def delete(name: String, validateOnly: Boolean)(implicit timeout: Timeout) = {
      if (validateOnly) Future(None) else actorFor[PersistenceActor] ? PersistenceActor.Delete(name, `type`)
    }

    protected def unmarshal: (String ⇒ T) = { (source: String) ⇒ unmarshaller.read(source) }

    protected def validate: (T ⇒ Future[T]) = { (artifact: T) ⇒ Future(artifact) }
  }

  class GatewayHandler extends PersistenceHandler[Gateway](GatewayReader) {

    override def create(source: String, validateOnly: Boolean)(implicit timeout: Timeout) = {
      unmarshal(source) match {
        case gateway ⇒ actorFor[GatewayActor] ? GatewayActor.Create(gateway, Option(source), validateOnly)
      }
    }

    override def update(name: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout) = {
      unmarshal(source) match {
        case gateway ⇒
          if (name != gateway.name) throwException(InconsistentArtifactName(name, gateway))
          actorFor[GatewayActor] ? GatewayActor.Update(gateway, Option(source), validateOnly)
      }
    }

    override def delete(name: String, validateOnly: Boolean)(implicit timeout: Timeout) = {
      actorFor[GatewayActor] ? GatewayActor.Delete(name, validateOnly)
    }
  }

  class ScheduledWorkflowHandler extends PersistenceHandler[ScheduledWorkflow](ScheduledWorkflowReader) {

    override def create(source: String, validateOnly: Boolean)(implicit timeout: Timeout) = super.create(source, validateOnly).map {
      case workflow: ScheduledWorkflow ⇒
        actorFor[WorkflowSchedulerActor] ! WorkflowSchedulerActor.Schedule(workflow)
        workflow
      case any ⇒ any
    }

    override def update(name: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout) = super.update(name, source, validateOnly).map {
      case workflow: ScheduledWorkflow ⇒
        actorFor[WorkflowSchedulerActor] ! WorkflowSchedulerActor.Schedule(workflow)
        workflow
      case any ⇒ any
    }

    override def delete(name: String, validateOnly: Boolean)(implicit timeout: Timeout) = super.delete(name, validateOnly).map {
      case Some(workflow: ScheduledWorkflow) ⇒
        actorFor[WorkflowSchedulerActor] ! WorkflowSchedulerActor.Unschedule(workflow)
        workflow
      case any ⇒ any
    }

    override protected def validate: (ScheduledWorkflow ⇒ Future[ScheduledWorkflow]) = { (scheduledWorkflow: ScheduledWorkflow) ⇒
      scheduledWorkflow.trigger match {
        case TimeTrigger(pattern) ⇒ if (!CronExpression.isValidExpression(pattern)) throwException(InvalidTimeTriggerError(pattern))
        case _ ⇒
      }

      artifactFor[DefaultWorkflow](scheduledWorkflow.workflow) map {
        case workflow ⇒
          workflow.requires.find(required ⇒ scheduledWorkflow.storage.get(required).isEmpty) match {
            case Some(required) ⇒ throwException(MissingRequiredVariableError(required))
            case _ ⇒
          }
          scheduledWorkflow
      }
    }
  }

}
