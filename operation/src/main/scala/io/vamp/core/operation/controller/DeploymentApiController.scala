package io.vamp.core.operation.controller

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka._
import io.vamp.common.notification.NotificationProvider
import io.vamp.core.model.artifact._
import io.vamp.core.model.conversion.DeploymentConversion._
import io.vamp.core.model.reader._
import io.vamp.core.operation.deployment.DeploymentActor
import io.vamp.core.persistence.{ArtifactResponseEnvelope, PersistenceActor}

import scala.concurrent.Future
import scala.language.{existentials, postfixOps}

trait DeploymentApiController {
  this: ActorSupport with FutureSupport with ExecutionContextProvider with NotificationProvider =>

  def deployments(asBlueprint: Boolean)(page: Int, perPage: Int)(implicit timeout: Timeout): Future[Any] = (actorFor(PersistenceActor) ? PersistenceActor.AllPaginated(classOf[Deployment], page, perPage)).map {
    case ArtifactResponseEnvelope(list, _, _, _) => list.map {
      case deployment: Deployment => if (asBlueprint) deployment.asBlueprint else deployment
      case any => any
    }
    case any => any
  }

  def deployment(name: String, asBlueprint: Boolean)(implicit timeout: Timeout): Future[Any] = (actorFor(PersistenceActor) ? PersistenceActor.Read(name, classOf[Deployment])).map {
    case Some(deployment: Deployment) => if (asBlueprint) deployment.asBlueprint else deployment
    case any => any
  }

  def createDeployment(request: String)(implicit timeout: Timeout) = DeploymentBlueprintReader.readReferenceFromSource(request) match {
    case blueprint: BlueprintReference => actorFor(DeploymentActor) ? DeploymentActor.Create(blueprint, request)
    case blueprint: DefaultBlueprint =>
      actorFor(PersistenceActor) ? PersistenceActor.Create(blueprint, Some(request), ignoreIfExists = true)
      actorFor(DeploymentActor) ? DeploymentActor.Create(blueprint, request)
  }

  def updateDeployment(name: String, request: String)(implicit timeout: Timeout): Future[Any] = DeploymentBlueprintReader.readReferenceFromSource(request) match {
    case blueprint: BlueprintReference => actorFor(DeploymentActor) ? DeploymentActor.Merge(name, blueprint, request)
    case blueprint: DefaultBlueprint =>
      actorFor(PersistenceActor) ? PersistenceActor.Create(blueprint, Some(request), ignoreIfExists = true)
      actorFor(DeploymentActor) ? DeploymentActor.Merge(name, blueprint, request)
  }

  def deleteDeployment(name: String, request: String)(implicit timeout: Timeout): Future[Any] = {
    if (request.nonEmpty)
      actorFor(DeploymentActor) ? DeploymentActor.Slice(name, DeploymentBlueprintReader.readReferenceFromSource(request), request)
    else
      actorFor(PersistenceActor) ? PersistenceActor.Read(name, classOf[Deployment])
  }

  def sla(deploymentName: String, clusterName: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result =>
      result.asInstanceOf[Option[Deployment]].flatMap(deployment => deployment.clusters.find(_.name == clusterName).flatMap(_.sla))
    }

  def slaUpdate(deploymentName: String, clusterName: String, request: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map {
      case Some(deployment: Deployment) =>
        deployment.clusters.find(_.name == clusterName) match {
          case None => None
          case Some(cluster) => offload(actorFor(DeploymentActor) ? DeploymentActor.UpdateSla(deployment, cluster, Some(SlaReader.read(request)), request))
        }
      case _ => None
    }

  def slaDelete(deploymentName: String, clusterName: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map {
      case Some(deployment: Deployment) =>
        deployment.clusters.find(_.name == clusterName) match {
          case None => None
          case Some(cluster) => offload(actorFor(DeploymentActor) ? DeploymentActor.UpdateSla(deployment, cluster, None, ""))
        }
      case _ => None
    }

  def scale(deploymentName: String, clusterName: String, breedName: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result =>
      result.asInstanceOf[Option[Deployment]].flatMap(deployment => deployment.clusters.find(_.name == clusterName).flatMap(cluster => cluster.services.find(_.breed.name == breedName)).flatMap(service => Some(service.scale)))
    }

  def scaleUpdate(deploymentName: String, clusterName: String, breedName: String, request: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map {
      case Some(deployment: Deployment) =>
        deployment.clusters.find(_.name == clusterName) match {
          case None => None
          case Some(cluster) => cluster.services.find(_.breed.name == breedName) match {
            case None => None
            case Some(service) =>
              val scale = ScaleReader.read(request) match {
                case s: ScaleReference => offload(actorFor(PersistenceActor) ? PersistenceActor.Read(s.name, classOf[Scale])).asInstanceOf[DefaultScale]
                case s: DefaultScale => s
              }
              offload(actorFor(DeploymentActor) ? DeploymentActor.UpdateScale(deployment, cluster, service, scale, request))
          }
        }
      case _ => None
    }

  def routing(deploymentName: String, clusterName: String, breedName: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map { result =>
      result.asInstanceOf[Option[Deployment]].flatMap(deployment => deployment.clusters.find(_.name == clusterName).flatMap(cluster => cluster.services.find(_.breed.name == breedName)).flatMap(service => Some(service.routing)))
    }

  def routingUpdate(deploymentName: String, clusterName: String, breedName: String, request: String)(implicit timeout: Timeout) =
    (actorFor(PersistenceActor) ? PersistenceActor.Read(deploymentName, classOf[Deployment])).map {
      case Some(deployment: Deployment) =>
        deployment.clusters.find(_.name == clusterName) match {
          case None => None
          case Some(cluster) => cluster.services.find(_.breed.name == breedName) match {
            case None => None
            case Some(service) =>
              val routing = RoutingReader.read(request) match {
                case r: RoutingReference => offload(actorFor(PersistenceActor) ? PersistenceActor.Read(r.name, classOf[Routing])).asInstanceOf[DefaultRouting]
                case r: DefaultRouting => r
              }
              offload(actorFor(DeploymentActor) ? DeploymentActor.UpdateRouting(deployment, cluster, service, routing, request))
          }
        }
      case _ => None
    }
}
