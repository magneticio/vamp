package io.vamp.core.persistence.actor

import _root_.io.vamp.common.vitals.InfoRequest
import akka.actor.{Actor, ActorLogging, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka._
import io.vamp.core.model.artifact.{Artifact, _}
import io.vamp.core.persistence.notification.{ArtifactNotFound, PersistenceNotificationProvider, UnsupportedPersistenceRequest}
import io.vamp.core.persistence.store.JdbcStoreProvider

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.existentials

object PersistenceActor extends ActorDescription {

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.core.model.persistence.response-timeout").seconds)

  def props(args: Any*): Props = Props[PersistenceActor]

  trait PersistenceMessages

  case class All(`type`: Class[_ <: Artifact]) extends PersistenceMessages

  case class Create(artifact: Artifact, source: Option[String] = None, ignoreIfExists: Boolean = true) extends PersistenceMessages

  case class Read(name: String, `type`: Class[_ <: Artifact]) extends PersistenceMessages

  case class Update(artifact: Artifact, source: Option[String] = None, create: Boolean = false) extends PersistenceMessages

  case class Delete(name: String, `type`: Class[_ <: Artifact]) extends PersistenceMessages

  case class ReadExpanded(name: String, `type`: Class[_ <: Artifact]) extends PersistenceMessages

}

class PersistenceActor extends JdbcStoreProvider with Actor with ActorLogging with ReplyActor with ArchivingProvider with FutureSupport with ActorExecutionContextProvider with PersistenceNotificationProvider {

  import PersistenceActor._

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

      case Create(artifact, source, ignoreIfExists) => Future {
        createDefaultArtifact(artifact, ignoreIfExists)
      } map (archiveCreate(_, source))

      case Read(name, ofType) => Future {
        readDefaultArtifact(name, ofType)
      }

      case ReadExpanded(name, ofType) => Future {
        readExpandedArtifact(name, ofType)
      }

      case Update(artifact, source, create) => Future {
        updateDefaultArtifact(artifact, create)
      } map (if (create) archiveCreate(_, source) else archiveUpdate(_, source))

      case Delete(name, ofType) => Future {
        deleteDefaultArtifact(name, ofType)
      } map archiveDelete

      case _ => error(errorRequest(request))
    }

    offload(future)
  }

  def info = store.info

  def createDefaultArtifact(artifact: Artifact, ignoreIfExists: Boolean): Artifact = store.create(artifact, ignoreIfExists)

  def getAllDefaultArtifacts(ofType: Class[_ <: Artifact]): List[_ <: Artifact] = store.all(ofType)

  def updateDefaultArtifact(artifact: Artifact, create: Boolean): Artifact = store.update(artifact, create)

  def deleteDefaultArtifact(name: String, ofType: Class[_ <: Artifact]): Artifact = store.delete(name, ofType)

  def readDefaultArtifact(name: String, ofType: Class[_ <: Artifact]): Option[Artifact] = store.read(name, ofType)


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
