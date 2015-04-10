package io.vamp.core.persistence.actor

import _root_.io.vamp.common.akka._
import _root_.io.vamp.common.notification.NotificationProvider
import _root_.io.vamp.common.vitals.InfoRequest
import _root_.io.vamp.core.model.artifact._
import _root_.io.vamp.core.persistence.notification.{ArtifactNotFound, PersistenceNotificationProvider, UnsupportedPersistenceRequest}
import akka.actor.{Actor, ActorLogging}
import akka.pattern.ask
import io.vamp.core.persistence.actor.PersistenceActor._

import scala.concurrent.Future
import scala.reflect._

/**
 * Framework for the Persistence Actor
 */
trait PersistingActor extends Actor with ActorLogging with ReplyActor with FutureSupport with ActorExecutionContextProvider with PersistenceNotificationProvider {


  lazy implicit val timeout = PersistenceActor.timeout

  override protected def requestType: Class[_] = classOf[PersistenceMessages]

  override protected def errorRequest(request: Any): RequestError = UnsupportedPersistenceRequest(request)

  def reply(request: Any) = {
    val future: Future[Any] = request match {
      case InfoRequest => Future {
        info
      }

      case All(ofType) => Future {
        getAllDefaultArtifacts(ofType)
      }

      case Create(artifact, ignoreIfExists) => Future {
        createDefaultArtifact(artifact, ignoreIfExists)
      }

      case Read(name, ofType) => Future {
        readDefaultArtifact(name, ofType)
      }

      case ReadExpanded(name, ofType) => Future {
        readExpandedArtifact(name, ofType)
      }

      case Update(artifact, create) => Future {
        updateDefaultArtifact(artifact, create)
      }

      case Delete(name, ofType) => Future {
        deleteDefaultArtifact(name, ofType)
      }

      case _ => error(errorRequest(request))
    }

    offload(future)
  }

  def info: Any

  def createDefaultArtifact(artifact: Artifact, ignoreIfExists: Boolean): Artifact

  def updateDefaultArtifact(artifact: Artifact, create: Boolean): Artifact

  def readDefaultArtifact(name: String, ofType: Class[_ <: Artifact]): Option[Artifact]

  def deleteDefaultArtifact(name: String, ofType: Class[_ <: Artifact]): Artifact

  def getAllDefaultArtifacts(ofType: Class[_ <: Artifact]): List[_ <: Artifact]

  private def readExpandedArtifact(name: String, ofType: Class[_ <: Artifact]): Option[Artifact] =
    readDefaultArtifact(name, ofType) match {
      case Some(deployment: Deployment) => Some(deployment) // Deployments are already fully expanded
      case Some(blueprint: DefaultBlueprint) => Some(blueprint.copy(clusters = expandClusters(blueprint.clusters)))
      case Some(breed: DefaultBreed) => Some(breed.copy(dependencies = expandDependencies(breed.dependencies)))
      case Some(sla: GenericSla) => Some(sla.copy(escalations = expandEscalations(sla.escalations)))
      case Some(sla: EscalationOnlySla) => Some(sla.copy(escalations = expandEscalations(sla.escalations)))
      case Some(sla: ResponseTimeSlidingWindowSla) => Some(sla.copy(escalations = expandEscalations(sla.escalations)))
      case Some(routing: DefaultRouting) => Some(routing.copy(filters = expandFilters(routing.filters)))
      case Some(escalation: GenericEscalation) => Some(escalation)
      case Some(filter: DefaultFilter) => Some(filter)
      case Some(scale: DefaultScale) => Some(scale)
      case _ => throw exception(UnsupportedPersistenceRequest(ofType))
    }

  private def expandClusters(clusters: List[Cluster]): List[Cluster] =
    clusters.map(cluster =>
      cluster.copy(services = expandServices(cluster.services),
        sla = cluster.sla match {
          case Some(sla: GenericSla) => Some(sla)
          case Some(sla: EscalationOnlySla) => Some(sla)
          case Some(sla: ResponseTimeSlidingWindowSla) => Some(sla)
          case Some(sla: SlaReference) => readDefaultArtifact(sla.name, classOf[GenericSla]) match {
            case Some(slaDefault: GenericSla) => Some(slaDefault.copy(escalations = sla.escalations)) //Copy the escalations from the reference
            case _ => throw exception(ArtifactNotFound(sla.name, classOf[GenericSla]))
          }
          case _ => None
        }
      )
    )

  private def expandServices(services: List[Service]): List[Service] =
    services.map(service =>
      service.copy(
        routing = service.routing match {
          case Some(routing: DefaultRouting) => Some(routing)
          case Some(routing: RoutingReference) => readExpandedArtifact(routing.name, classOf[DefaultRouting]) match {
            case Some(slaDefault: DefaultRouting) => Some(slaDefault)
            case _ => throw exception(ArtifactNotFound(routing.name, classOf[DefaultRouting]))
          }
          case _ => None
        },
        scale = service.scale match {
          case Some(scale: DefaultScale) => Some(scale)
          case Some(scale: ScaleReference) => readExpandedArtifact(scale.name, classOf[DefaultScale]) match {
            case Some(scaleDefault: DefaultScale) => Some(scaleDefault)
            case _ => throw exception(ArtifactNotFound(scale.name, classOf[DefaultScale]))
          }
          case _ => None
        },
        breed = replaceBreed(service.breed)
      )
    )

  private def replaceBreed(breed: Breed): DefaultBreed = breed match {
    case defaultBreed: DefaultBreed => defaultBreed
    case breedReference: BreedReference => readExpandedArtifact(breedReference.name, classOf[DefaultBreed]) match {
      case Some(defaultBreed: DefaultBreed) => defaultBreed
      case _ => throw exception(ArtifactNotFound(breedReference.name, classOf[DefaultBreed]))
    }
    case _ => throw exception(ArtifactNotFound(breed.name, classOf[DefaultBreed]))
  }

  private def expandDependencies(list: Map[String, Breed]): Map[String, Breed] =
    list.map(item => item._1 -> replaceBreed(item._2))

  private def expandFilters(list: List[Filter]): List[DefaultFilter] =
    list.map {
      case referencedArtifact: FilterReference =>
        readDefaultArtifact(referencedArtifact.name, classOf[DefaultFilter]) match {
          case Some(defaultArtifact: DefaultFilter) => defaultArtifact
          case _ => throw exception(ArtifactNotFound(referencedArtifact.name, classOf[DefaultFilter]))
        }
      case defaultArtifact: DefaultFilter => defaultArtifact
    }

  private def expandEscalations(list: List[Escalation]): List[GenericEscalation] =
    list.map {
      case referencedArtifact: EscalationReference =>
        readDefaultArtifact(referencedArtifact.name, classOf[GenericEscalation]) match {
          case Some(defaultArtifact: GenericEscalation) => defaultArtifact
          case _ => throw exception(ArtifactNotFound(referencedArtifact.name, classOf[GenericEscalation]))
        }
      case defaultArtifact: GenericEscalation => defaultArtifact
    }

}

trait ArtifactSupport {
  this: FutureSupport with ActorSupport with NotificationProvider =>

  def artifactFor[T <: Artifact : ClassTag](artifact: Option[Artifact]): Option[T] = artifact match {
    case None => None
    case Some(a) => Some(artifactFor[T](a))
  }

  def artifactFor[T <: Artifact : ClassTag](artifact: Artifact): T = artifact match {
    case a: T => a
    case _ => artifactFor[T](artifact.name)
  }

  def artifactFor[T <: Artifact : ClassTag](name: String): T = {
    implicit val timeout = PersistenceActor.timeout
    offload(actorFor(PersistenceActor) ? PersistenceActor.Read(name, classTag[T].runtimeClass.asInstanceOf[Class[Artifact]])) match {
      case Some(artifact: T) => artifact
      case _ => error(ArtifactNotFound(name, classTag[T].runtimeClass))
    }
  }
}

