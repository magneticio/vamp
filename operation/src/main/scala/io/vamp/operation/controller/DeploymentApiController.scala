package io.vamp.operation.controller

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.IoC._
import io.vamp.common.akka._
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._
import io.vamp.model.conversion.DeploymentConversion._
import io.vamp.model.reader._
import io.vamp.operation.deployment.DeploymentActor
import io.vamp.persistence.notification.PersistenceOperationFailure
import io.vamp.persistence.{ ArtifactResponseEnvelope, ArtifactShrinkage, PersistenceActor }

import scala.concurrent.Future
import scala.language.{ existentials, postfixOps }

trait DeploymentApiController extends ArtifactShrinkage {
  this: ExecutionContextProvider with NotificationProvider with ActorSystemProvider ⇒

  def deployments(asBlueprint: Boolean, expandReferences: Boolean, onlyReferences: Boolean)(page: Int, perPage: Int)(implicit timeout: Timeout): Future[ArtifactResponseEnvelope] = {
    (actorFor[PersistenceActor] ? PersistenceActor.All(classOf[Deployment], page, perPage, expandReferences, onlyReferences)) map {
      case envelope: ArtifactResponseEnvelope ⇒ envelope.copy(response = envelope.response.map {
        case deployment: Deployment ⇒ transform(deployment, asBlueprint, onlyReferences)
        case any                    ⇒ any
      })
      case other ⇒ throwException(PersistenceOperationFailure(other))
    }
  }

  def deployment(name: String, asBlueprint: Boolean, expandReferences: Boolean, onlyReferences: Boolean)(implicit timeout: Timeout): Future[Any] = (actorFor[PersistenceActor] ? PersistenceActor.Read(name, classOf[Deployment], expandReferences, onlyReferences)).map {
    case Some(deployment: Deployment) ⇒ transform(deployment, asBlueprint, onlyReferences)
    case any                          ⇒ any
  }

  private def transform(deployment: Deployment, asBlueprint: Boolean, onlyRef: Boolean) = {
    if (asBlueprint) {
      val blueprint = deployment.asBlueprint
      if (onlyRef) onlyReferences(blueprint) else blueprint
    } else deployment
  }

  def createDeployment(request: String, validateOnly: Boolean)(implicit timeout: Timeout) = DeploymentBlueprintReader.readReferenceFromSource(request) match {
    case blueprint: BlueprintReference ⇒ actorFor[DeploymentActor] ? DeploymentActor.Create(blueprint, request, validateOnly)
    case blueprint: DefaultBlueprint ⇒
      if (!validateOnly) actorFor[PersistenceActor] ? PersistenceActor.Create(blueprint, Some(request), ignoreIfExists = true)
      actorFor[DeploymentActor] ? DeploymentActor.Create(blueprint, request, validateOnly)
  }

  def updateDeployment(name: String, request: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = DeploymentBlueprintReader.readReferenceFromSource(request) match {
    case blueprint: BlueprintReference ⇒ actorFor[DeploymentActor] ? DeploymentActor.Merge(name, blueprint, request, validateOnly)
    case blueprint: DefaultBlueprint ⇒
      if (!validateOnly) actorFor[PersistenceActor] ? PersistenceActor.Create(blueprint, Some(request), ignoreIfExists = true)
      actorFor[DeploymentActor] ? DeploymentActor.Merge(name, blueprint, request, validateOnly)
  }

  def deleteDeployment(name: String, request: String, validateOnly: Boolean)(implicit timeout: Timeout): Future[Any] = {
    if (request.nonEmpty)
      actorFor[DeploymentActor] ? DeploymentActor.Slice(name, DeploymentBlueprintReader.readReferenceFromSource(request), request, validateOnly)
    else
      actorFor[PersistenceActor] ? PersistenceActor.Read(name, classOf[Deployment])
  }

  def sla(deploymentName: String, clusterName: String)(implicit timeout: Timeout) =
    (actorFor[PersistenceActor] ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result ⇒
      result.asInstanceOf[Option[Deployment]].flatMap(deployment ⇒ deployment.clusters.find(_.name == clusterName).flatMap(_.sla))
    }

  def slaUpdate(deploymentName: String, clusterName: String, request: String)(implicit timeout: Timeout) =
    actorFor[PersistenceActor] ? PersistenceActor.Read(deploymentName, classOf[Deployment]) flatMap {
      case Some(deployment: Deployment) ⇒
        deployment.clusters.find(_.name == clusterName) match {
          case None          ⇒ Future(None)
          case Some(cluster) ⇒ actorFor[DeploymentActor] ? DeploymentActor.UpdateSla(deployment, cluster, Some(SlaReader.read(request)), request)
        }
      case _ ⇒ Future(None)
    }

  def slaDelete(deploymentName: String, clusterName: String)(implicit timeout: Timeout) =
    actorFor[PersistenceActor] ? PersistenceActor.Read(deploymentName, classOf[Deployment]) flatMap {
      case Some(deployment: Deployment) ⇒
        deployment.clusters.find(_.name == clusterName) match {
          case None          ⇒ Future(None)
          case Some(cluster) ⇒ actorFor[DeploymentActor] ? DeploymentActor.UpdateSla(deployment, cluster, None, "")
        }
      case _ ⇒ Future(None)
    }

  def scale(deploymentName: String, clusterName: String, breedName: String)(implicit timeout: Timeout) =
    (actorFor[PersistenceActor] ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result ⇒
      result.asInstanceOf[Option[Deployment]].flatMap(deployment ⇒ deployment.clusters.find(_.name == clusterName).flatMap(cluster ⇒ cluster.services.find(_.breed.name == breedName)).flatMap(service ⇒ Some(service.scale)))
    }

  def scaleUpdate(deploymentName: String, clusterName: String, breedName: String, request: String)(implicit timeout: Timeout) =
    actorFor[PersistenceActor] ? PersistenceActor.Read(deploymentName, classOf[Deployment]) flatMap {
      case Some(deployment: Deployment) ⇒
        deployment.clusters.find(_.name == clusterName) match {
          case None ⇒ Future(None)
          case Some(cluster) ⇒ cluster.services.find(_.breed.name == breedName) match {
            case None ⇒ Future(None)
            case Some(service) ⇒
              (ScaleReader.read(request) match {
                case s: ScaleReference ⇒ actorFor[PersistenceActor] ? PersistenceActor.Read(s.name, classOf[Scale])
                case s: DefaultScale   ⇒ Future(s)
              }).map {
                case scale: DefaultScale ⇒ actorFor[DeploymentActor] ? DeploymentActor.UpdateScale(deployment, cluster, service, scale, request)
                case _                   ⇒ Future(None)
              }
          }
        }
      case _ ⇒ Future(None)
    }

  def routing(deploymentName: String, clusterName: String)(implicit timeout: Timeout) =
    (actorFor[PersistenceActor] ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result ⇒
      result.asInstanceOf[Option[Deployment]].flatMap(deployment ⇒ deployment.clusters.find(_.name == clusterName).flatMap(cluster ⇒ Some(cluster.routing)))
    }

  def routingUpdate(deploymentName: String, clusterName: String, request: String)(implicit timeout: Timeout) =
    actorFor[PersistenceActor] ? PersistenceActor.Read(deploymentName, classOf[Deployment]) flatMap {
      case Some(deployment: Deployment) ⇒
        deployment.clusters.find(_.name == clusterName) match {
          case None          ⇒ Future(None)
          case Some(cluster) ⇒ actorFor[DeploymentActor] ? DeploymentActor.UpdateRouting(deployment, cluster, RoutingReader.read(request), request)
        }
      case _ ⇒ Future(None)
    }
}
