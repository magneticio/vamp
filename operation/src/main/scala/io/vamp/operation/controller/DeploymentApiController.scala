package io.vamp.operation.controller

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.Namespace
import io.vamp.common.akka.IoC._
import io.vamp.common.notification.NotificationErrorException
import io.vamp.model.artifact._
import io.vamp.model.conversion.DeploymentConversion._
import io.vamp.model.reader._
import io.vamp.operation.deployment.DeploymentActor
import io.vamp.persistence.refactor.VampPersistence
import io.vamp.persistence.refactor.serialization.VampJsonFormats
import io.vamp.persistence.{ ArtifactResponseEnvelope, ArtifactShrinkage }
import io.vamp.common.Id

import scala.concurrent.Future

trait DeploymentApiController extends SourceTransformer with ArtifactShrinkage with AbstractController with VampJsonFormats {

  def deployments(asBlueprint: Boolean, expandReferences: Boolean, onlyReferences: Boolean)(page: Int, perPage: Int)(implicit namespace: Namespace, timeout: Timeout): Future[ArtifactResponseEnvelope] = {
    val fromAndSize = if (perPage > 0) Some((perPage * page, perPage)) else None
    VampPersistence().getAll[Deployment](fromAndSize) map { deploymentList ⇒
      ArtifactResponseEnvelope(
        response = deploymentList.map { deployment ⇒ transform(deployment, asBlueprint, onlyReferences) },
        total = deploymentList.size, //TODO: changet his to an appropriate value
        page = page, perPage = perPage)
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
              blueprint.clusters.flatMap(_.services).map(_.breed).filter(_.isInstanceOf[DefaultBreed]).map { b ⇒
                VampPersistence().create[Breed](b)
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
