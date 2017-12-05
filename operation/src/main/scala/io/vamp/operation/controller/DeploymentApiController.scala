package io.vamp.operation.controller

import akka.actor.FSM.Failure
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.{Artifact, Id, Namespace, UnitPlaceholder}
import io.vamp.common.akka.IoC._
import io.vamp.common.notification.NotificationErrorException
import io.vamp.model.artifact._
import io.vamp.model.conversion.DeploymentConversion._
import io.vamp.model.reader._
import io.vamp.operation.deployment.DeploymentActor
import io.vamp.operation.notification.UnsupportedDeploymentRequest
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import io.vamp.persistence.{ArtifactResponseEnvelope, ArtifactShrinkage}

import scala.concurrent.Future
import scala.util.Try

trait DeploymentApiController extends SourceTransformer with ArtifactShrinkage with AbstractController with VampJsonFormats {

  def getDeployments(asBlueprint: Boolean, expandReferences: Boolean, onlyReferences: Boolean, page: Int, perPage: Int)(implicit namespace: Namespace, timeout: Timeout): Future[ArtifactResponseEnvelope] = {
    val actualPage = if(page < 1) 0 else page-1
    val fromAndSize = if (perPage > 0) Some((perPage * actualPage, perPage)) else None
    VampPersistence().getAll[Deployment](fromAndSize) map { searchResponse ⇒
      ArtifactResponseEnvelope(
        response = searchResponse.response.map { deployment ⇒ transform(deployment, asBlueprint, onlyReferences) },
        total = searchResponse.total,
        page = searchResponse.from / searchResponse.size, perPage = searchResponse.size)
    }
  }

  def getDeployment(name: String, asBlueprint: Boolean, expandReferences: Boolean, onlyReferences: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Blueprint] =
    VampPersistence().read[Deployment](Id[Deployment](name)) map (transform(_, asBlueprint, onlyReferences))

  private def transform(deployment: Deployment, asBlueprint: Boolean, onlyRef: Boolean): Blueprint = {
    if (asBlueprint) {
      val blueprint = deployment.asDefaultBlueprint
      if (onlyRef) onlyReferences(blueprint).asInstanceOf[DefaultBlueprint] else blueprint
    }
    else deployment
  }

  def createDeployment(source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[UnitPlaceholder] = {
    def triggerCreateOperation(blueprint: Blueprint): Future[Unit] = (actorFor[DeploymentActor] ? DeploymentActor.Create(blueprint, source, validateOnly)).map(_ => ())

    for {
      request <- sourceImport(source)
      _ <- asBlueprint(request) match {
        case blueprint: BlueprintReference ⇒ triggerCreateOperation(blueprint)
        case blueprint: DefaultBlueprint ⇒
          val futures = {
            if (!validateOnly)
              blueprint.clusters.flatMap(_.services).map(_.breed).filter(_.isInstanceOf[DefaultBreed]).map {
                breed ⇒ VampPersistence().create[Breed](breed)
              }
            else Nil
          } :+ triggerCreateOperation(blueprint)
          Future.sequence(futures)
        case _ => Future.failed(reportException(UnsupportedDeploymentRequest(source)))
      }
    } yield UnitPlaceholder
  }

  def updateDeployment(name: String, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[UnitPlaceholder] = {
    def triggerUpdateOperation(blueprint: Blueprint): Future[Unit] = (actorFor[DeploymentActor] ? DeploymentActor.Merge(name, blueprint, source, validateOnly)).map(_ => ())
    for {
      request <- sourceImport(source)
      _ <- asBlueprint(request) match {
        case blueprint: BlueprintReference ⇒ triggerUpdateOperation(blueprint)
        case blueprint: DefaultBlueprint ⇒
          val futures = {
            if (!validateOnly)
              blueprint.clusters.flatMap(_.services.map(_.breed)).filter(_.isInstanceOf[DefaultBreed]).map(VampPersistence().createOrUpdate[Breed](_))
            else Nil
          } :+ triggerUpdateOperation(blueprint)
          Future.sequence(futures)
      }
    } yield UnitPlaceholder
  }


  private def asBlueprint(source: String): Blueprint = {
    Try(DeploymentBlueprintReader.readReferenceFromSource(source)) match {
      case scala.util.Failure(e) if(e.isInstanceOf[NotificationErrorException]) => DeploymentReader.readReferenceFromSource(source)
      case scala.util.Failure(e) => throw e
      case scala.util.Success(s) => s
    }
  }


  def deleteDeployment(name: String, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[UnitPlaceholder] = {
    if(!source.isEmpty) for {
        afterImport <- sourceImport(source)
        _ <- (actorFor[DeploymentActor] ? DeploymentActor.Slice(name, asBlueprint(afterImport), source, validateOnly))
      } yield UnitPlaceholder
    else Future.successful(UnitPlaceholder)
  }

  def sla(deploymentName: String, clusterName: String)(implicit namespace: Namespace, timeout: Timeout): Future[Option[Sla]] =
    (VampPersistence().readIfAvailable[Deployment](Id[Deployment](deploymentName))).map { result ⇒
      result.flatMap(deployment ⇒ deployment.clusters.find(_.name == clusterName).flatMap(_.sla))
    }

  def slaUpdate(deploymentName: String, clusterName: String, request: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[UnitPlaceholder] =
    VampPersistence().read[Deployment](Id[Deployment](deploymentName)) flatMap { deployment ⇒
      deployment.clusters.find(_.name == clusterName) match {
        case None          ⇒ Future.successful(UnitPlaceholder)
        case Some(cluster) ⇒ (actorFor[DeploymentActor] ? DeploymentActor.UpdateSla(deployment, cluster, Some(SlaReader.read(request)), request, validateOnly)).map(_ => UnitPlaceholder)
      }
    }

  def slaDelete(deploymentName: String, clusterName: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[UnitPlaceholder] =
    VampPersistence().read[Deployment](Id[Deployment](deploymentName)) flatMap { deployment ⇒
      deployment.clusters.find(_.name == clusterName) match {
        case None          ⇒ Future.successful(UnitPlaceholder)
        case Some(cluster) ⇒ (actorFor[DeploymentActor] ? DeploymentActor.UpdateSla(deployment, cluster, None, "", validateOnly)).map(_ => UnitPlaceholder)
      }
    }

  def scale(deploymentName: String, clusterName: String, breedName: String)(implicit namespace: Namespace, timeout: Timeout): Future[Option[DefaultScale]] =
    (VampPersistence().readIfAvailable[Deployment](Id[Deployment](deploymentName))).map { result ⇒
      result.flatMap(deployment ⇒ deployment.clusters.find(_.name == clusterName).flatMap(cluster ⇒ cluster.services.find(_.breed.name == breedName)).flatMap(_.scale))
    }

  def scaleUpdate(deploymentName: String, clusterName: String, breedName: String, request: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) =
    VampPersistence().read[Deployment](Id[Deployment](deploymentName)) flatMap { deployment ⇒
      deployment.clusters.find(_.name == clusterName) match {
        case None ⇒ Future.successful(UnitPlaceholder)
        case Some(cluster) ⇒ cluster.services.find(_.breed.name == breedName) match {
          case None ⇒ Future.successful(UnitPlaceholder)
          case Some(service) ⇒
            (ScaleReader.read(request) match {
              case s: ScaleReference ⇒ VampPersistence().read[Scale](Id[Scale](s.name))
              case s: DefaultScale   ⇒ Future.successful(s)
            }).map {
              case scale: DefaultScale ⇒ (actorFor[DeploymentActor] ? DeploymentActor.UpdateScale(deployment, cluster, service, scale, request, validateOnly)).map(_ => UnitPlaceholder)
              case _                   ⇒ Future.successful(UnitPlaceholder)
            }
        }
      }
    }
}
