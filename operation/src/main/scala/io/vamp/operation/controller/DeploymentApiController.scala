package io.vamp.operation.controller

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.{Id, Namespace}
import io.vamp.common.akka.IoC._
import io.vamp.common.notification.NotificationErrorException
import io.vamp.model.artifact._
import io.vamp.model.conversion.DeploymentConversion._
import io.vamp.model.reader._
import io.vamp.operation.deployment.DeploymentActor
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import io.vamp.persistence.{ArtifactResponseEnvelope, ArtifactShrinkage}

import scala.concurrent.Future

trait DeploymentApiController extends SourceTransformer with ArtifactShrinkage with AbstractController with VampJsonFormats {

  def getDeployments(asBlueprint: Boolean, expandReferences: Boolean, onlyReferences: Boolean)(page: Int, perPage: Int)(implicit namespace: Namespace, timeout: Timeout): Future[ArtifactResponseEnvelope] = {
    val actualPage = if(page < 1) 0 else page-1
    val fromAndSize = if (perPage > 0) Some((perPage * actualPage, perPage)) else None
    VampPersistence().getAll[Deployment](fromAndSize) map { searchResponse ⇒
      ArtifactResponseEnvelope(
        response = searchResponse.response.map { deployment ⇒ transform(deployment, asBlueprint, onlyReferences) },
        total = searchResponse.total,
        page = searchResponse.from / searchResponse.size, perPage = searchResponse.size)
    }
  }

  def deployment(name: String, asBlueprint: Boolean, expandReferences: Boolean, onlyReferences: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] =
    VampPersistence().read[Deployment](Id[Deployment](name)) map { deployment ⇒ transform(deployment, asBlueprint, onlyReferences) }

  private def transform(deployment: Deployment, asBlueprint: Boolean, onlyRef: Boolean) = {
    if (asBlueprint) {
      val blueprint = deployment.asBlueprint
      if (onlyRef) onlyReferences(blueprint) else blueprint
    }
    else deployment
  }

  def createDeployment(source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) = {

    def default(blueprint: Blueprint) = actorFor[DeploymentActor] ? DeploymentActor.Create(blueprint, source, validateOnly)

    sourceImport(source).flatMap { request ⇒
      processBlueprint(request, {
        case blueprint: BlueprintReference ⇒ default(blueprint)
        case blueprint: DefaultBlueprint ⇒

          val futures = {
            if (!validateOnly)
              blueprint.clusters.flatMap(_.services).map(_.breed).filter(_.isInstanceOf[DefaultBreed]).map {
                breed ⇒ VampPersistence().create[Breed](breed)
              }
            else Nil
          } :+ default(blueprint)

          Future.sequence(futures)
      })
    }
  }

  def updateDeployment(name: String, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = {

    def default(blueprint: Blueprint) = actorFor[DeploymentActor] ? DeploymentActor.Merge(name, blueprint, source, validateOnly)

    sourceImport(source).flatMap { request ⇒
      processBlueprint(request, {
        case blueprint: BlueprintReference ⇒ default(blueprint)
        case blueprint: DefaultBlueprint ⇒

          val futures = {
            if (!validateOnly)
              for {
                cluster <- blueprint.clusters
                service <- cluster.services
                breed = service.breed
                if(breed.isInstanceOf[DefaultBreed])
              } yield {
                for {
                  existingBreed <- VampPersistence().readIfAvailable[Breed](breedSerilizationSpecifier.idExtractor(breed))
                  _ <- existingBreed match {
                    case None => VampPersistence().create[Breed](breed)
                    case Some(_) => VampPersistence().update[Breed](breedSerilizationSpecifier.idExtractor(breed), _ => breed)
                  }
                } yield ()
              }
            else Nil
          } :+ default(blueprint)

          Future.sequence(futures)
      })
    }
  }

  private def processBlueprint(request: String, process: (Blueprint) ⇒ Future[Any]) = try {
    process {
      DeploymentBlueprintReader.readReferenceFromSource(request)
    }
  }
  catch {
    case e: NotificationErrorException ⇒
      try {
        process(DeploymentReader.readReferenceFromSource(request).asBlueprint)
      }
      catch {
        case _: Exception ⇒ throw e
      }
  }

  def deleteDeployment(name: String, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = {
    if (source.nonEmpty) {
      sourceImport(source).flatMap { request ⇒
        processBlueprint(request, {
          blueprint ⇒ actorFor[DeploymentActor] ? DeploymentActor.Slice(name, blueprint, source, validateOnly)
        })
      }
    }
    else {
      VampPersistence().read[Deployment](Id[Deployment](name))
    }
  }

  def sla(deploymentName: String, clusterName: String)(implicit namespace: Namespace, timeout: Timeout) =
    (VampPersistence().read[Deployment](Id[Deployment](deploymentName))).map { result ⇒
      result.asInstanceOf[Option[Deployment]].flatMap(deployment ⇒ deployment.clusters.find(_.name == clusterName).flatMap(_.sla))
    }

  def slaUpdate(deploymentName: String, clusterName: String, request: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) =
    VampPersistence().read[Deployment](Id[Deployment](deploymentName)) flatMap { deployment ⇒
      deployment.clusters.find(_.name == clusterName) match {
        case None          ⇒ Future(None)
        case Some(cluster) ⇒ actorFor[DeploymentActor] ? DeploymentActor.UpdateSla(deployment, cluster, Some(SlaReader.read(request)), request, validateOnly)
      }
    }

  def slaDelete(deploymentName: String, clusterName: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) =
    VampPersistence().read[Deployment](Id[Deployment](deploymentName)) flatMap { deployment ⇒
      deployment.clusters.find(_.name == clusterName) match {
        case None          ⇒ Future(None)
        case Some(cluster) ⇒ actorFor[DeploymentActor] ? DeploymentActor.UpdateSla(deployment, cluster, None, "", validateOnly)
      }
    }

  def scale(deploymentName: String, clusterName: String, breedName: String)(implicit namespace: Namespace, timeout: Timeout) =
    (VampPersistence().read[Deployment](Id[Deployment](deploymentName))).map { result ⇒
      result.asInstanceOf[Option[Deployment]].flatMap(deployment ⇒ deployment.clusters.find(_.name == clusterName).flatMap(cluster ⇒ cluster.services.find(_.breed.name == breedName)).flatMap(service ⇒ Some(service.scale)))
    }

  def scaleUpdate(deploymentName: String, clusterName: String, breedName: String, request: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) =
    VampPersistence().read[Deployment](Id[Deployment](deploymentName)) flatMap { deployment ⇒
      deployment.clusters.find(_.name == clusterName) match {
        case None ⇒ Future(None)
        case Some(cluster) ⇒ cluster.services.find(_.breed.name == breedName) match {
          case None ⇒ Future(None)
          case Some(service) ⇒
            (ScaleReader.read(request) match {
              case s: ScaleReference ⇒ VampPersistence().read[Scale](Id[Scale](s.name))
              case s: DefaultScale   ⇒ Future(s)
            }).map {
              case scale: DefaultScale ⇒ actorFor[DeploymentActor] ? DeploymentActor.UpdateScale(deployment, cluster, service, scale, request, validateOnly)
              case _                   ⇒ Future(None)
            }
        }
      }
    }
}
