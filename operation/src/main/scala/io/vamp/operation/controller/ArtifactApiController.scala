package io.vamp.operation.controller

import java.net.URLDecoder

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.{ Artifact, Namespace }
import io.vamp.common.akka.IoC._
import io.vamp.model.artifact._
import io.vamp.model.notification.InconsistentArtifactName
import io.vamp.model.reader.{ YamlReader, _ }
import io.vamp.operation.notification.UnexpectedArtifact
import io.vamp.persistence.notification.PersistenceOperationFailure
import io.vamp.persistence.{ ArtifactExpansionSupport, ArtifactResponseEnvelope, PersistenceActor }

import scala.concurrent.Future

trait ArtifactApiController
    extends DeploymentApiController
    with GatewayApiController
    with WorkflowApiController
    with MultipleArtifactApiController
    with SingleArtifactApiController
    with ArtifactExpansionSupport
    with AbstractController {

  def background(artifact: String): Boolean = !crud(artifact)

  def readArtifacts(kind: String, expandReferences: Boolean, onlyReferences: Boolean)(page: Int, perPage: Int)(implicit namespace: Namespace, timeout: Timeout): Future[ArtifactResponseEnvelope] = `type`(kind) match {
    case (t, _) if t == classOf[Deployment] ⇒ Future.successful(ArtifactResponseEnvelope(Nil, 0, 1, ArtifactResponseEnvelope.maxPerPage))
    case (t, _) ⇒
      actorFor[PersistenceActor] ? PersistenceActor.All(t, page, perPage, expandReferences, onlyReferences) map {
        case envelope: ArtifactResponseEnvelope ⇒ envelope
        case other                              ⇒ throwException(PersistenceOperationFailure(other))
      }
  }
}

trait SingleArtifactApiController extends AbstractController {
  this: ArtifactApiController ⇒

  def createArtifact(kind: String, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = `type`(kind) match {
    case (t, _) if t == classOf[Deployment] ⇒ throwException(UnexpectedArtifact(kind))
    case (t, r) if t == classOf[Gateway]    ⇒ createGateway(r, source, validateOnly)
    case (t, r) if t == classOf[Workflow]   ⇒ createWorkflow(r, source, validateOnly)
    case (_, r)                             ⇒ create(r, source, validateOnly)
  }

  def readArtifact(kind: String, name: String, expandReferences: Boolean, onlyReferences: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = `type`(kind) match {
    case (t, _) if t == classOf[Gateway]    ⇒ actorFor[PersistenceActor] ? PersistenceActor.Read(URLDecoder.decode(name, "UTF-8"), t, expandReferences, onlyReferences)
    case (t, _) if t == classOf[Deployment] ⇒ Future.successful(None)
    case (t, _)                             ⇒ read(t, name, expandReferences, onlyReferences)
  }

  def updateArtifact(kind: String, name: String, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = `type`(kind) match {
    case (t, _) if t == classOf[Deployment] ⇒ throwException(UnexpectedArtifact(kind))
    case (t, r) if t == classOf[Gateway]    ⇒ updateGateway(r, name, source, validateOnly)
    case (t, r) if t == classOf[Workflow]   ⇒ updateWorkflow(r, name, source, validateOnly)
    case (_, r)                             ⇒ update(r, name, source, validateOnly)
  }

  def deleteArtifact(kind: String, name: String, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = `type`(kind) match {
    case (t, _) if t == classOf[Deployment] ⇒ Future.successful(None)
    case (t, _) if t == classOf[Gateway]    ⇒ deleteGateway(name, validateOnly)
    case (t, _) if t == classOf[Workflow]   ⇒ deleteWorkflow(read(t, name, expandReferences = false, onlyReferences = false), source, validateOnly)
    case (t, _)                             ⇒ delete(t, name, validateOnly)
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

  private def read(`type`: Class[_ <: Artifact], name: String, expandReferences: Boolean, onlyReferences: Boolean)(implicit namespace: Namespace, timeout: Timeout) = {
    actorFor[PersistenceActor] ? PersistenceActor.Read(name, `type`, expandReferences, onlyReferences)
  }

  private def create(reader: YamlReader[_ <: Artifact], source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) = {
    reader.read(source) match {
      case artifact ⇒ if (validateOnly) Future.successful(artifact) else actorFor[PersistenceActor] ? PersistenceActor.Create(artifact, Option(source))
    }
  }

  private def update(reader: YamlReader[_ <: Artifact], name: String, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) = {
    reader.read(source) match {
      case artifact ⇒
        if (name != artifact.name)
          throwException(InconsistentArtifactName(name, artifact.name))

        if (validateOnly) Future.successful(artifact) else actorFor[PersistenceActor] ? PersistenceActor.Update(artifact, Some(source))
    }
  }

  private def delete(`type`: Class[_ <: Artifact], name: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) = {
    if (validateOnly) Future.successful(None) else actorFor[PersistenceActor] ? PersistenceActor.Delete(name, `type`)
  }
}

trait MultipleArtifactApiController extends AbstractController {
  this: SingleArtifactApiController with DeploymentApiController ⇒

  def createArtifacts(source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = process(source, {
    item ⇒
      `type`(item.kind) match {
        case (t, _) if t == classOf[Deployment] ⇒ createDeployment(item.toString, validateOnly)
        case _                                  ⇒ createArtifact(item.kind, item.toString, validateOnly)
      }
  })

  def updateArtifacts(source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = process(source, {
    item ⇒
      `type`(item.kind) match {
        case (t, _) if t == classOf[Deployment] ⇒ updateDeployment(item.name, item.toString, validateOnly)
        case _                                  ⇒ updateArtifact(item.kind, item.name, item.toString, validateOnly)
      }
  })

  def deleteArtifacts(source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = process(source, {
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
