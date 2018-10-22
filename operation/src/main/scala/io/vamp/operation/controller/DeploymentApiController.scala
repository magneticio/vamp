package io.vamp.operation.controller

import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import io.vamp.common.Namespace
import io.vamp.common.akka.IoC._
import io.vamp.common.notification.NotificationErrorException
import io.vamp.model.artifact._
import io.vamp.model.conversion.DeploymentConversion._
import io.vamp.model.reader._
import io.vamp.operation.deployment.DeploymentActor
import io.vamp.persistence.notification.PersistenceOperationFailure
import io.vamp.persistence.{ ArtifactResponseEnvelope, ArtifactShrinkage, PersistenceActor }

import scala.concurrent.Future

trait DeploymentApiController extends SourceTransformer with ArtifactShrinkage with AbstractController with LazyLogging {

  // These methods allows 3rd party users to override these methods
  protected def userDefinedOverrides(deployment: Deployment)(implicit namespace: Namespace, timeout: Timeout): Deployment = deployment
  protected def userDefinedOverrides(blueprint: Blueprint)(implicit namespace: Namespace, timeout: Timeout): Blueprint = blueprint
  protected def userDefinedOverrides(defaultBlueprint: DefaultBlueprint)(implicit namespace: Namespace, timeout: Timeout): DefaultBlueprint = defaultBlueprint

  def deployments(asBlueprint: Boolean, expandReferences: Boolean, onlyReferences: Boolean)(page: Int, perPage: Int)(implicit namespace: Namespace, timeout: Timeout): Future[ArtifactResponseEnvelope] = {
    (actorFor[PersistenceActor] ? PersistenceActor.All(classOf[Deployment], page, perPage, expandReferences, onlyReferences)) map {
      case envelope: ArtifactResponseEnvelope ⇒ envelope.copy(response = envelope.response.map {
        case deployment: Deployment ⇒ transform(deployment, asBlueprint, onlyReferences)
        case any                    ⇒ any
      })
      case other ⇒ throwException(PersistenceOperationFailure(other))
    }
  }

  def deployment(name: String, asBlueprint: Boolean, expandReferences: Boolean, onlyReferences: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = (actorFor[PersistenceActor] ? PersistenceActor.Read(name, classOf[Deployment], expandReferences, onlyReferences)).map {
    case Some(deployment: Deployment) ⇒ transform(deployment, asBlueprint, onlyReferences)
    case other                        ⇒ other
  }

  protected def transform(deployment: Deployment, asBlueprint: Boolean, onlyRef: Boolean)(implicit namespace: Namespace, timeout: Timeout) = {
    if (asBlueprint) {
      val blueprint = userDefinedOverrides(deployment.asBlueprint)
      if (onlyRef) onlyReferences(userDefinedOverrides(blueprint)) else userDefinedOverrides(blueprint)
    }
    else userDefinedOverrides(deployment)
  }

  def createDeployment(source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) = {

    def default(blueprint: Blueprint) = actorFor[DeploymentActor] ? DeploymentActor.Create(blueprint, source, validateOnly)

    logger.info("Deployment Api Controller create Deployment or Blueprint with source: {}", source)

    sourceImport(source).flatMap { request ⇒
      processBlueprint(request, {
        case blueprint: BlueprintReference ⇒ {
          logger.info("Deployment Api Controller update Deployment Blueprint Reference {}", blueprint.name)
          default(userDefinedOverrides(blueprint))
        }
        case blueprint: DefaultBlueprint ⇒

          val futures = {
            if (!validateOnly)
              userDefinedOverrides(blueprint).clusters.flatMap(_.services).map(_.breed).filter(_.isInstanceOf[DefaultBreed]).map {
                breed ⇒
                  {
                    logger.info("Deployment Api Controller createDeployment {} with source: {}", breed.name, source)
                    actorFor[PersistenceActor] ? PersistenceActor.Create(breed, Some(source))
                  }
              }
            else Nil
          } :+ default(userDefinedOverrides(blueprint))

          Future.sequence(futures)
      })
    }
  }

  def updateDeployment(name: String, source: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout): Future[Any] = {

    def default(blueprint: Blueprint) = actorFor[DeploymentActor] ? DeploymentActor.Merge(name, blueprint, source, validateOnly)

    logger.info("Deployment Api Controller update Deployment or Blueprint with source: {}", source)

    sourceImport(source).flatMap { request ⇒
      processBlueprint(request, {
        case blueprint: BlueprintReference ⇒ {
          logger.info("Deployment Api Controller update Deployment Blueprint Reference {}", blueprint.name)
          default(userDefinedOverrides(blueprint))
        }
        case blueprint: DefaultBlueprint ⇒

          val futures = {
            if (!validateOnly)
              userDefinedOverrides(blueprint).clusters.flatMap(_.services).map(_.breed).filter(_.isInstanceOf[DefaultBreed]).map {
                breed ⇒
                  {
                    logger.info("Deployment Api Controller updateDeployment {} with source: {} breed: {}", name, source, breed.toString)
                    actorFor[PersistenceActor] ? PersistenceActor.Create(breed, Some(source))
                  }
              }
            else Nil
          } :+ default(userDefinedOverrides(blueprint))

          Future.sequence(futures)

        case unexpected =>
          logger.error(s"Deployment Api Controller - Unexpedted type was received ${unexpected.getClass.getName}")
          throw new RuntimeException(s"Deployment Api Controller - Unexpedted type was received ${unexpected.getClass.getName}")
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
      actorFor[PersistenceActor] ? PersistenceActor.Read(name, classOf[Deployment])
    }
  }

  def sla(deploymentName: String, clusterName: String)(implicit namespace: Namespace, timeout: Timeout) =
    (actorFor[PersistenceActor] ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result ⇒
      result.asInstanceOf[Option[Deployment]].flatMap(deployment ⇒ deployment.clusters.find(_.name == clusterName).flatMap(_.sla))
    }

  def slaUpdate(deploymentName: String, clusterName: String, request: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) =
    actorFor[PersistenceActor] ? PersistenceActor.Read(deploymentName, classOf[Deployment]) flatMap {
      case Some(deployment: Deployment) ⇒
        deployment.clusters.find(_.name == clusterName) match {
          case None          ⇒ Future(None)
          case Some(cluster) ⇒ actorFor[DeploymentActor] ? DeploymentActor.UpdateSla(deployment, cluster, Some(SlaReader.read(request)), request, validateOnly)
        }
      case _ ⇒ Future(None)
    }

  def slaDelete(deploymentName: String, clusterName: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) =
    actorFor[PersistenceActor] ? PersistenceActor.Read(deploymentName, classOf[Deployment]) flatMap {
      case Some(deployment: Deployment) ⇒
        deployment.clusters.find(_.name == clusterName) match {
          case None          ⇒ Future(None)
          case Some(cluster) ⇒ actorFor[DeploymentActor] ? DeploymentActor.UpdateSla(deployment, cluster, None, "", validateOnly)
        }
      case _ ⇒ Future(None)
    }

  def scale(deploymentName: String, clusterName: String, breedName: String)(implicit namespace: Namespace, timeout: Timeout) =
    (actorFor[PersistenceActor] ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result ⇒
      result.asInstanceOf[Option[Deployment]].flatMap(deployment ⇒ deployment.clusters.find(_.name == clusterName).flatMap(cluster ⇒ cluster.services.find(_.breed.name == breedName)).flatMap(service ⇒ Some(service.scale)))
    }

  def scaleUpdate(deploymentName: String, clusterName: String, breedName: String, request: String, validateOnly: Boolean)(implicit namespace: Namespace, timeout: Timeout) =
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
                case scale: DefaultScale ⇒ actorFor[DeploymentActor] ? DeploymentActor.UpdateScale(deployment, cluster, service, scale, request, validateOnly)
                case _                   ⇒ Future(None)
              }
          }
        }
      case _ ⇒ Future(None)
    }
}
