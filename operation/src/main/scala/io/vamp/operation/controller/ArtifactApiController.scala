package io.vamp.operation.controller

import java.net.URLDecoder

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.IoC._
import io.vamp.common.akka.{ActorSystemProvider, ExecutionContextProvider}
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._
import io.vamp.model.notification.InconsistentArtifactName
import io.vamp.model.reader.{YamlReader, _}
import io.vamp.operation.gateway.GatewayActor
import io.vamp.operation.notification.UnexpectedArtifact
import io.vamp.persistence.db._
import io.vamp.persistence.notification.PersistenceOperationFailure

import scala.concurrent.Future

trait ArtifactApiController extends MultipleArtifactApiController with SingleArtifactApiController with ArtifactExpansionSupport {
  this: DeploymentApiController with ExecutionContextProvider with NotificationProvider with ActorSystemProvider ⇒

  def background(artifact: String): Boolean = !crud(artifact)

  def readArtifacts(kind: String, expandReferences: Boolean, onlyReferences: Boolean)(page: Int, perPage: Int)(implicit timeout: Timeout): Future[ArtifactResponseEnvelope] = `type`(kind) match {
    case (t, _) if t == classOf[Deployment] ⇒ Future(ArtifactResponseEnvelope(Nil, 0, 1, ArtifactResponseEnvelope.maxPerPage))
    case (t, _) ⇒
      actorFor[PersistenceActor] ? PersistenceActor.All(t, page, perPage, expandReferences, onlyReferences) map {
        case envelope: ArtifactResponseEnvelope ⇒ envelope
        case other                              ⇒ throwException(PersistenceOperationFailure(other))
      }
  }
}

trait SingleArtifactApiController {
  this: ArtifactApiController with ExecutionContextProvider with NotificationProvider with ActorSystemProvider ⇒

  import PersistenceActor._

  def createArtifact(kind: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = `type`(kind) match {
    case (t, r) if t == classOf[Gateway] ⇒
      expandGateway(r.read(source).asInstanceOf[Gateway]) flatMap { gateway ⇒
        actorFor[GatewayActor] ? GatewayActor.Create(gateway, Option(source), validateOnly)
      }

    case (t, _) if t == classOf[Deployment] ⇒ throwException(UnexpectedArtifact(kind))

    case (t, r) if t == classOf[Workflow] ⇒
      create(r, source, validateOnly).map {
        case list: List[_] ⇒
          if (!validateOnly) list.foreach { case workflow: Workflow ⇒ actorFor[PersistenceActor] ? ResetWorkflow(workflow) }
          list
        case any ⇒ any
      }

    case (_, r) ⇒ create(r, source, validateOnly)
  }

  def readArtifact(kind: String, name: String, expandReferences: Boolean, onlyReferences: Boolean)(implicit timeout: Timeout): Future[Any] = `type`(kind) match {
    case (t, _) if t == classOf[Gateway]    ⇒ actorFor[PersistenceActor] ? PersistenceActor.Read(URLDecoder.decode(name, "UTF-8"), t, expandReferences, onlyReferences)
    case (t, _) if t == classOf[Deployment] ⇒ Future.successful(None)
    case (t, _)                             ⇒ read(t, name, expandReferences, onlyReferences)
  }

  def updateArtifact(kind: String, name: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = `type`(kind) match {
    case (t, r) if t == classOf[Gateway] ⇒

      expandGateway(r.read(source).asInstanceOf[Gateway]) flatMap { gateway ⇒
        if (name != gateway.name) throwException(InconsistentArtifactName(name, gateway.name))
        actorFor[GatewayActor] ? GatewayActor.Update(gateway, Option(source), validateOnly, promote = true)
      }

    case (t, r) if t == classOf[Deployment] ⇒ throwException(UnexpectedArtifact(kind))

    case (t, r) if t == classOf[Workflow] ⇒
      update(r, name, source, validateOnly).map {
        case list: List[_] ⇒
          if (!validateOnly) list.foreach { case workflow: Workflow ⇒ actorFor[PersistenceActor] ? ResetWorkflow(workflow) }
          list
        case any ⇒ any
      }

    case (_, r) ⇒ update(r, name, source, validateOnly)
  }

  def deleteArtifact(kind: String, name: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = `type`(kind) match {
    case (t, r) if t == classOf[Gateway] ⇒
      actorFor[GatewayActor] ? GatewayActor.Delete(name, validateOnly)

    case (t, r) if t == classOf[Deployment] ⇒ Future.successful(None)

    case (t, r) if t == classOf[Workflow] ⇒
      read(t, name, expandReferences = false, onlyReferences = false) map {
        case Some(workflow: Workflow) ⇒
          if (validateOnly) Future.successful(true)
          else
            (actorFor[PersistenceActor] ? PersistenceActor.Update(workflow.copy(status = Workflow.Status.Stopping), Some(source))).flatMap { _ ⇒
              actorFor[PersistenceActor] ? UpdateWorkflowStatus(workflow, Workflow.Status.Stopping)
            }
        case _ ⇒ false
      }

    case (t, r) ⇒ delete(t, name, validateOnly)
  }

  protected def crud(kind: String): Boolean = `type`(kind) match {
    case (t, _) if t == classOf[Gateway]    ⇒ false
    case (t, _) if t == classOf[Deployment] ⇒ false
    case (t, _) if t == classOf[Workflow]   ⇒ false
    case _                                  ⇒ true
  }

  protected def `type`(kind: String): (Class[_ <: Artifact], YamlReader[_ <: Artifact]) = kind match {
    case "breeds"      ⇒ (classOf[Breed], BreedReader)
    case "blueprints"  ⇒ (classOf[Blueprint], BlueprintReader)
    case "slas"        ⇒ (classOf[Sla], SlaReader)
    case "scales"      ⇒ (classOf[Scale], ScaleReader)
    case "escalations" ⇒ (classOf[Escalation], EscalationReader)
    case "routes"      ⇒ (classOf[Route], RouteReader)
    case "conditions"  ⇒ (classOf[Condition], ConditionReader)
    case "rewrites"    ⇒ (classOf[Rewrite], RewriteReader)
    case "workflows"   ⇒ (classOf[Workflow], WorkflowReader)
    case "gateways"    ⇒ (classOf[Gateway], GatewayReader)
    case "deployments" ⇒ (classOf[Deployment], DeploymentReader)
    case _             ⇒ throwException(UnexpectedArtifact(kind))
  }

  private def read(`type`: Class[_ <: Artifact], name: String, expandReferences: Boolean, onlyReferences: Boolean)(implicit timeout: Timeout) = {
    actorFor[PersistenceActor] ? PersistenceActor.Read(name, `type`, expandReferences, onlyReferences)
  }

  private def create(reader: YamlReader[_ <: Artifact], source: String, validateOnly: Boolean)(implicit timeout: Timeout) = {
    reader.read(source) match {
      case artifact ⇒ if (validateOnly) Future(artifact) else actorFor[PersistenceActor] ? PersistenceActor.Create(artifact, Option(source))
    }
  }

  private def update(reader: YamlReader[_ <: Artifact], name: String, source: String, validateOnly: Boolean)(implicit timeout: Timeout) = {
    reader.read(source) match {
      case artifact ⇒
        if (name != artifact.name)
          throwException(InconsistentArtifactName(name, artifact.name))

        if (validateOnly) Future(artifact) else actorFor[PersistenceActor] ? PersistenceActor.Update(artifact, Some(source))
    }
  }

  private def delete(`type`: Class[_ <: Artifact], name: String, validateOnly: Boolean)(implicit timeout: Timeout) = {
    if (validateOnly) Future(None) else actorFor[PersistenceActor] ? PersistenceActor.Delete(name, `type`)
  }
}

trait MultipleArtifactApiController {
  this: SingleArtifactApiController with DeploymentApiController with ExecutionContextProvider with NotificationProvider with ActorSystemProvider ⇒

  def createArtifacts(source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = process(source, {
    item ⇒
      `type`(item.kind) match {
        case (t, _) if t == classOf[Deployment] ⇒ createDeployment(item.toString, validateOnly)
        case _                                  ⇒ createArtifact(item.kind, item.toString, validateOnly)
      }
  })

  def updateArtifacts(source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = process(source, {
    item ⇒
      `type`(item.kind) match {
        case (t, _) if t == classOf[Deployment] ⇒ updateDeployment(item.name, item.toString, validateOnly)
        case _                                  ⇒ updateArtifact(item.kind, item.name, item.toString, validateOnly)
      }
  })

  def deleteArtifacts(source: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = process(source, {
    item ⇒
      `type`(item.kind) match {
        case (t, _) if t == classOf[Deployment] ⇒ deleteDeployment(item.name, item.toString, validateOnly)
        case _                                  ⇒ deleteArtifact(item.kind, item.name, item.toString, validateOnly)
      }
  })

  private def process(source: String, execute: ArtifactSource ⇒ Future[Any]) = Future.sequence {
    ArtifactListReader.read(source).map(execute)
  }
}
