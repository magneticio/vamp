package io.vamp.operation.controller

import java.net.URLDecoder

import akka.util.Timeout
import io.vamp.common.akka.IoC.actorFor
import io.vamp.common.{Artifact, Id, Namespace, UnitPlaceholder}
import io.vamp.model.artifact._
import io.vamp.model.notification.{ImportReferenceError, InconsistentArtifactName}
import io.vamp.model.reader.{YamlReader, _}
import io.vamp.model.serialization.CoreSerializationFormat
import io.vamp.operation.gateway.GatewayActor
import io.vamp.operation.notification.UnexpectedArtifact
import io.vamp.persistence.notification.PersistenceOperationFailure
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.api.SearchResponse
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import io.vamp.persistence.{ArtifactExpansionSupport, ArtifactResponseEnvelope}
import org.json4s.native.Serialization.write
import org.json4s.{DefaultFormats, Extraction}
import akka.pattern.ask

import scala.concurrent.Future

trait ArtifactApiController
    extends DeploymentApiController
    with WorkflowApiController
    with MultipleArtifactApiController
    with SingleArtifactApiController
    with ArtifactExpansionSupport
    with AbstractController with VampJsonFormats {

  def background(artifact: String)(implicit namespace: Namespace): Boolean = !crud(artifact)

  private def searchResponseToArtifactResponseEnvelope[T <: Artifact](searchResponse: SearchResponse[T], fromAndSize: Option[(Int, Int)]): ArtifactResponseEnvelope = {
    ArtifactResponseEnvelope(
      response = searchResponse.response,
      total = searchResponse.total,
      page = searchResponse.from / searchResponse.size, perPage = searchResponse.size)
  }

  def readArtifacts(kind: String, expandReferences: Boolean, onlyReferences: Boolean)(page: Int, perPage: Int)(implicit namespace: Namespace, timeout: Timeout): Future[ArtifactResponseEnvelope] = {
    val actualPage = if(page < 1) 0 else page-1
    val fromAndSize = if (perPage > 0) Some((perPage * actualPage, perPage)) else None
    `type`(kind) match {
      case (t, _) if t == classOf[Deployment] ⇒ Future.successful(ArtifactResponseEnvelope(Nil, 0, 1, ArtifactResponseEnvelope.maxPerPage))
      case (t, _) if t == classOf[Breed]      ⇒ VampPersistence().getAll[Breed](fromAndSize).map(searchResponseToArtifactResponseEnvelope(_, fromAndSize))
      case (t, _) if t == classOf[Sla]        ⇒ VampPersistence().getAll[Sla](fromAndSize).map(searchResponseToArtifactResponseEnvelope(_, fromAndSize))
      case (t, _) if t == classOf[Escalation] ⇒ VampPersistence().getAll[Escalation](fromAndSize).map(searchResponseToArtifactResponseEnvelope(_, fromAndSize))
      case (t, _) if t == classOf[Route]      ⇒ VampPersistence().getAll[Route](fromAndSize).map(searchResponseToArtifactResponseEnvelope(_, fromAndSize))
      case (t, _) if t == classOf[Condition]  ⇒ VampPersistence().getAll[Condition](fromAndSize).map(searchResponseToArtifactResponseEnvelope(_, fromAndSize))
      case (t, _) if t == classOf[Rewrite]    ⇒ VampPersistence().getAll[Rewrite](fromAndSize).map(searchResponseToArtifactResponseEnvelope(_, fromAndSize))
      case (t, _) if t == classOf[Workflow]   ⇒ VampPersistence().getAll[Workflow](fromAndSize).map(searchResponseToArtifactResponseEnvelope(_, fromAndSize))
      case (t, _) if t == classOf[Gateway]    ⇒ VampPersistence().getAll[Gateway](fromAndSize).map(searchResponseToArtifactResponseEnvelope(_, fromAndSize))
      case (t, _) if t == classOf[Template]   ⇒ VampPersistence().getAll[Template](fromAndSize).map(searchResponseToArtifactResponseEnvelope(_, fromAndSize))
      case (t, _) if t == classOf[Scale]      ⇒ VampPersistence().getAll[Scale](fromAndSize).map(searchResponseToArtifactResponseEnvelope(_, fromAndSize))
      case (t, _) if t == classOf[Blueprint]  ⇒ VampPersistence().getAll[Blueprint](fromAndSize).map(searchResponseToArtifactResponseEnvelope(_, fromAndSize))
      case other                              ⇒ throwException(PersistenceOperationFailure(other))
    }
  }
}

trait SingleArtifactApiController extends SourceTransformer with AbstractController {
  this: ArtifactApiController ⇒

  def createArtifact(kind: String, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = `type`(kind) match {
    case (t, _) if t == classOf[Deployment] ⇒ throwException(UnexpectedArtifact(kind))
    case (t, r) if t == classOf[Gateway]    ⇒ unmarshall(r, source).flatMap(artifact => expandGateway(artifact.asInstanceOf[Gateway]) flatMap { gateway ⇒
      (actorFor[GatewayActor] ? GatewayActor.Create(gateway, Option(source), validateOnly))
    })
    case (t, r) if t == classOf[Workflow]   ⇒ unmarshall(r, source).flatMap(x ⇒ createWorkflow(x.asInstanceOf[Workflow], validateOnly))
    case (_, r)                             ⇒ unmarshall(r, source).flatMap(create(_, source, validateOnly))
  }

  def readArtifact(kind: String, name: String, expandReferences: Boolean, onlyReferences: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Option[Artifact]] = `type`(kind) match {
    case (t, _) if t == classOf[Gateway]    ⇒ VampPersistence().read[Gateway](Id[Gateway](URLDecoder.decode(name, "UTF-8"))).map(x ⇒ Some(x))
    case (t, _) if t == classOf[Deployment] ⇒ Future.successful(None)
    case (t, _) if t == classOf[Breed]      ⇒ VampPersistence().read[Breed](Id[Breed](name)).map(x ⇒ Some(x))
    case (t, _) if t == classOf[Sla]        ⇒ VampPersistence().read[Sla](Id[Sla](name)).map(x ⇒ Some(x))
    case (t, _) if t == classOf[Escalation] ⇒ VampPersistence().read[Escalation](Id[Escalation](name)).map(x ⇒ Some(x))
    case (t, _) if t == classOf[Route]      ⇒ VampPersistence().read[Route](Id[Route](name)).map(x ⇒ Some(x))
    case (t, _) if t == classOf[Condition]  ⇒ VampPersistence().read[Condition](Id[Condition](name)).map(x ⇒ Some(x))
    case (t, _) if t == classOf[Rewrite]    ⇒ VampPersistence().read[Rewrite](Id[Rewrite](name)).map(x ⇒ Some(x))
    case (t, _) if t == classOf[Workflow]   ⇒ VampPersistence().read[Workflow](Id[Workflow](name)).map(x ⇒ Some(x))
    case (t, _) if t == classOf[Template]   ⇒ VampPersistence().read[Template](Id[Template](name)).map(x ⇒ Some(x))
    case (t, _) if t == classOf[Scale]      ⇒ VampPersistence().read[Scale](Id[Scale](name)).map(x ⇒ Some(x))
    case (t, _) if t == classOf[Blueprint]  ⇒ VampPersistence().read[Blueprint](Id[Blueprint](name)).map(x ⇒ Some(x))
    case other                              ⇒ throwException(PersistenceOperationFailure(other))
  }

  def updateArtifact(kind: String, name: String, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = `type`(kind) match {
    case (t, _) if t == classOf[Deployment] ⇒ throwException(UnexpectedArtifact(kind))
    case (t, r) if t == classOf[Gateway]    ⇒ unmarshall(r, source).flatMap(artifact =>
      expandGateway(artifact.asInstanceOf[Gateway]) flatMap { gateway ⇒
        if (name != gateway.name) throwException(InconsistentArtifactName(name, gateway.name))
        actorFor[GatewayActor] ? GatewayActor.Update(gateway, Option(source), validateOnly, promote = true)
      }
    )
    case (t, r) if t == classOf[Workflow]   ⇒ unmarshall(r, source).flatMap(x ⇒ updateWorkflow(x.asInstanceOf[Workflow], name, validateOnly))
    case (_, r)                             ⇒ unmarshall(r, source).flatMap(update(_, name, source, validateOnly))
  }

  def deleteArtifact(kind: String, name: String, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = `type`(kind) match {
    case (t, _) if t == classOf[Deployment] ⇒ Future.successful(None)
    case (t, _) if t == classOf[Gateway]    ⇒ (actorFor[GatewayActor] ? GatewayActor.Delete(name, validateOnly))
    case (t, _) if t == classOf[Workflow]   ⇒ {
      for {
        existingWorkflow <- VampPersistence().readIfAvailable[Workflow](Id[Workflow](name))
        _ <- existingWorkflow match {
          case None => Future.successful()
          case Some(workflow) => VampPersistence().update[Workflow](workflowSerilizationSpecifier.idExtractor(workflow), _.copy(status = Workflow.Status.Stopping))
        }
      } yield UnitPlaceholder
    }
    case (t, _)                             ⇒ delete(t, name, validateOnly)
  }

  protected def crud(kind: String)(implicit namespace: Namespace): Boolean = `type`(kind) match {
    case (t, _) if t == classOf[Gateway]    ⇒ false
    case (t, _) if t == classOf[Deployment] ⇒ false
    case (t, _) if t == classOf[Workflow]   ⇒ false
    case _                                  ⇒ true
  }

  private def read(`type`: Class[_ <: Artifact], name: String, expandReferences: Boolean, onlyReferences: Boolean)(implicit namespace: Namespace, timeout: Timeout) = {
    `type` match {
      case t if t == classOf[Gateway]    ⇒ VampPersistence().read[Gateway](Id[Gateway](URLDecoder.decode(name, "UTF-8"))).map(x ⇒ Some(x))
      case t if t == classOf[Deployment] ⇒ Future.successful(None)
      case t if t == classOf[Breed]      ⇒ VampPersistence().read[Breed](Id[Breed](name)).map(x ⇒ Some(x))
      case t if t == classOf[Sla]        ⇒ VampPersistence().read[Sla](Id[Sla](name)).map(x ⇒ Some(x))
      case t if t == classOf[Escalation] ⇒ VampPersistence().read[Escalation](Id[Escalation](name)).map(x ⇒ Some(x))
      case t if t == classOf[Route]      ⇒ VampPersistence().read[Route](Id[Route](name)).map(x ⇒ Some(x))
      case t if t == classOf[Condition]  ⇒ VampPersistence().read[Condition](Id[Condition](name)).map(x ⇒ Some(x))
      case t if t == classOf[Rewrite]    ⇒ VampPersistence().read[Rewrite](Id[Rewrite](name)).map(x ⇒ Some(x))
      case t if t == classOf[Workflow]   ⇒ VampPersistence().read[Workflow](Id[Workflow](name)).map(x ⇒ Some(x))
      case t if t == classOf[Template]   ⇒ VampPersistence().read[Template](Id[Template](name)).map(x ⇒ Some(x))
      case t if t == classOf[Scale]      ⇒ VampPersistence().read[Scale](Id[Scale](name)).map(x ⇒ Some(x))
      case t if t == classOf[Blueprint]  ⇒ VampPersistence().read[Blueprint](Id[Blueprint](name)).map(x ⇒ Some(x))
      case other                         ⇒ throwException(PersistenceOperationFailure(other))
    }
  }

  private def create(artifact: Artifact, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) = {
    if(validateOnly)
      Future.successful(artifact)
    else artifact match {
      case e: Gateway    ⇒ VampPersistence().create[Gateway](e)
      case e: Deployment ⇒ VampPersistence().create[Deployment](e)
      case e: Breed      ⇒ VampPersistence().create[Breed](e)
      case e: Sla        ⇒ VampPersistence().create[Sla](e)
      case e: Escalation ⇒ VampPersistence().create[Escalation](e)
      case e: Route      ⇒ VampPersistence().create[Route](e)
      case e: Condition  ⇒ VampPersistence().create[Condition](e)
      case e: Rewrite    ⇒ VampPersistence().create[Rewrite](e)
      case e: Workflow   ⇒ VampPersistence().create[Workflow](e)
      case e: Template   ⇒ VampPersistence().create[Template](e)
      case e: Scale      ⇒ VampPersistence().create[Scale](e)
      case e: Blueprint  ⇒ {
        for {
          _ <- VampPersistence().create[Blueprint](e)
          defaultBreedsThatNeedCreation = e match {
            case d: DefaultBlueprint => d.clusters.flatMap(_.services.map(_.breed)).filter(_.isInstanceOf[DefaultBreed]).map(_.asInstanceOf[DefaultBreed])
            case _ => Nil
          }
          _ <- Future.sequence(defaultBreedsThatNeedCreation.map(b => VampPersistence().create[Breed](b)))
        } yield ()
      }
      case other         ⇒ throwException(PersistenceOperationFailure(other))
    }
  }

  private def update(artifact: Artifact, name: String, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) = {
    if (name != artifact.name)
      throwException(InconsistentArtifactName(name, artifact.name))
    if (validateOnly) Future.successful(artifact) else
      artifact match {
        case e: Gateway    ⇒ VampPersistence().update[Gateway](Id[Gateway](e.name), _ ⇒ e)
        case e: Deployment ⇒ VampPersistence().update[Deployment](Id[Deployment](e.name), _ ⇒ e)
        case e: Breed      ⇒ VampPersistence().update[Breed](Id[Breed](e.name), _ ⇒ e)
        case e: Sla        ⇒ VampPersistence().update[Sla](Id[Sla](e.name), _ ⇒ e)
        case e: Escalation ⇒ VampPersistence().update[Escalation](Id[Escalation](e.name), _ ⇒ e)
        case e: Route      ⇒ VampPersistence().update[Route](Id[Route](e.name), _ ⇒ e)
        case e: Condition  ⇒ VampPersistence().update[Condition](Id[Condition](e.name), _ ⇒ e)
        case e: Rewrite    ⇒ VampPersistence().update[Rewrite](Id[Rewrite](e.name), _ ⇒ e)
        case e: Workflow   ⇒ VampPersistence().update[Workflow](Id[Workflow](e.name), _ ⇒ e)
        case e: Template   ⇒ VampPersistence().update[Template](Id[Template](e.name), _ ⇒ e)
        case e: Scale      ⇒ VampPersistence().update[Scale](Id[Scale](e.name), _ ⇒ e)
        case e: Blueprint  ⇒ VampPersistence().update[Blueprint](Id[Blueprint](e.name), _ ⇒ e)
        case other         ⇒ throwException(PersistenceOperationFailure(other))
      }
  }

  private def delete(`type`: Class[_ <: Artifact], name: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) = {
    if (validateOnly) Future.successful(None) else
      `type` match {
        case t if t == classOf[Gateway]    ⇒ VampPersistence().deleteObject[Gateway](Id[Gateway](URLDecoder.decode(name, "UTF-8"))).map(x ⇒ Some(x))
        case t if t == classOf[Deployment] ⇒ VampPersistence().deleteObject[Deployment](Id[Deployment](name))
        case t if t == classOf[Breed]      ⇒ VampPersistence().deleteObject[Breed](Id[Breed](name)).map(x ⇒ Some(x))
        case t if t == classOf[Sla]        ⇒ VampPersistence().deleteObject[Sla](Id[Sla](name)).map(x ⇒ Some(x))
        case t if t == classOf[Escalation] ⇒ VampPersistence().deleteObject[Escalation](Id[Escalation](name)).map(x ⇒ Some(x))
        case t if t == classOf[Route]      ⇒ VampPersistence().deleteObject[Route](Id[Route](name)).map(x ⇒ Some(x))
        case t if t == classOf[Condition]  ⇒ VampPersistence().deleteObject[Condition](Id[Condition](name)).map(x ⇒ Some(x))
        case t if t == classOf[Rewrite]    ⇒ VampPersistence().deleteObject[Rewrite](Id[Rewrite](name)).map(x ⇒ Some(x))
        case t if t == classOf[Workflow]   ⇒ VampPersistence().deleteObject[Workflow](Id[Workflow](name)).map(x ⇒ Some(x))
        case t if t == classOf[Template]   ⇒ VampPersistence().deleteObject[Template](Id[Template](name)).map(x ⇒ Some(x))
        case t if t == classOf[Scale]      ⇒ VampPersistence().deleteObject[Scale](Id[Scale](name)).map(x ⇒ Some(x))
        case t if t == classOf[Blueprint]  ⇒ VampPersistence().deleteObject[Blueprint](Id[Blueprint](name)).map(x ⇒ Some(x))
        case other                         ⇒ throwException(PersistenceOperationFailure(other))
      }
  }

  private def unmarshall(reader: YamlReader[_ <: Artifact], source: String)(implicit namespace: Namespace, timeout: Timeout): Future[Artifact] = {
    sourceImport(source).map(reader.read(_))
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

trait SourceTransformer extends VampJsonFormats {
  this: AbstractController ⇒

  def sourceImport(source: String)(implicit namespace: Namespace, timeout: Timeout): Future[String] = {
    val artifact = ImportReader.read(source)
    Future.sequence(artifact.references.map { ref ⇒
      {
        val (kind, _) = `type`(ref.kind)
        kind match {
          case t if t == classOf[Gateway]    ⇒ VampPersistence().read[Gateway](Id[Gateway](URLDecoder.decode(ref.name, "UTF-8"))).map(x ⇒ Some(x))
          case t if t == classOf[Deployment] ⇒ Future.successful(None)
          case t if t == classOf[Breed]      ⇒ VampPersistence().read[Breed](Id[Breed](ref.name)).map(x ⇒ Some(x))
          case t if t == classOf[Sla]        ⇒ VampPersistence().read[Sla](Id[Sla](ref.name)).map(x ⇒ Some(x))
          case t if t == classOf[Escalation] ⇒ VampPersistence().read[Escalation](Id[Escalation](ref.name)).map(x ⇒ Some(x))
          case t if t == classOf[Route]      ⇒ VampPersistence().read[Route](Id[Route](ref.name)).map(x ⇒ Some(x))
          case t if t == classOf[Condition]  ⇒ VampPersistence().read[Condition](Id[Condition](ref.name)).map(x ⇒ Some(x))
          case t if t == classOf[Rewrite]    ⇒ VampPersistence().read[Rewrite](Id[Rewrite](ref.name)).map(x ⇒ Some(x))
          case t if t == classOf[Workflow]   ⇒ VampPersistence().read[Workflow](Id[Workflow](ref.name)).map(x ⇒ Some(x))
          case t if t == classOf[Template]   ⇒ VampPersistence().read[Template](Id[Template](ref.name)).map(x ⇒ Some(x))
          case t if t == classOf[Scale]      ⇒ VampPersistence().read[Scale](Id[Scale](ref.name)).map(x ⇒ Some(x))
          case t if t == classOf[Blueprint]  ⇒ VampPersistence().read[Blueprint](Id[Blueprint](ref.name)).map(x ⇒ Some(x))
          case other                         ⇒ throwException(PersistenceOperationFailure(other))
        }
      }.map(r ⇒ ref → r)
    }).map { imports ⇒
      val decomposed = imports.map {
        case (_, Some(t: Template)) ⇒ Extraction.decompose(t.definition)(DefaultFormats)
        case (_, Some(other))       ⇒ Extraction.decompose(other)(CoreSerializationFormat.default)
        case (r, _)                 ⇒ throwException(ImportReferenceError(r.toString))
      }
      val expanded = {
        if (decomposed.isEmpty)
          Extraction.decompose(artifact.base)(DefaultFormats)
        else
          decomposed.reduceLeft { (a, b) ⇒ a merge b }.merge(Extraction.decompose(artifact.base)(DefaultFormats))
      }
      write(expanded)(DefaultFormats)
    }
  }

  protected def `type`(kind: String)(implicit namespace: Namespace): (Class[_ <: Artifact], YamlReader[_ <: Artifact]) = kind match {
    case Breed.kind      ⇒ (classOf[Breed], BreedReader)
    case Blueprint.kind  ⇒ (classOf[Blueprint], BlueprintReader)
    case Sla.kind        ⇒ (classOf[Sla], SlaReader)
    case Scale.kind      ⇒ (classOf[Scale], ScaleReader)
    case Escalation.kind ⇒ (classOf[Escalation], EscalationReader)
    case Route.kind      ⇒ (classOf[Route], RouteReader)
    case Condition.kind  ⇒ (classOf[Condition], ConditionReader)
    case Rewrite.kind    ⇒ (classOf[Rewrite], RewriteReader)
    case Workflow.kind   ⇒ (classOf[Workflow], WorkflowReader)
    case Gateway.kind    ⇒ (classOf[Gateway], GatewayReader)
    case Deployment.kind ⇒ (classOf[Deployment], DeploymentReader)
    case Template.kind   ⇒ (classOf[Template], TemplateReader)
    case _               ⇒ throwException(UnexpectedArtifact(kind))
  }
}
