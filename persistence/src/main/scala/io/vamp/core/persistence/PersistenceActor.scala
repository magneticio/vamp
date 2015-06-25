package io.vamp.core.persistence

import _root_.io.vamp.common.vitals.InfoRequest
import akka.actor.Props
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka._
import io.vamp.common.http.OffsetResponseEnvelope
import io.vamp.common.notification.NotificationErrorException
import io.vamp.core.model.artifact.{Artifact, _}
import io.vamp.core.persistence.notification._

import scala.concurrent.duration._
import scala.language.existentials

object ArtifactResponseEnvelope {
  val maxPerPage = 30
}

case class ArtifactResponseEnvelope(response: List[Artifact], total: Long, page: Int, perPage: Int) extends OffsetResponseEnvelope[Artifact]

object PersistenceActor extends ActorDescription {

  def props(args: Any*): Props = Props.empty

  lazy val timeout = Timeout(ConfigFactory.load().getInt("vamp.core.persistence.response-timeout").seconds)

  trait PersistenceMessages

  case class All(`type`: Class[_ <: Artifact]) extends PersistenceMessages

  case class AllPaginated(`type`: Class[_ <: Artifact], page: Int, perPage: Int) extends PersistenceMessages

  case class Create(artifact: Artifact, source: Option[String] = None, ignoreIfExists: Boolean = true) extends PersistenceMessages

  case class Read(name: String, `type`: Class[_ <: Artifact]) extends PersistenceMessages

  case class Update(artifact: Artifact, source: Option[String] = None, create: Boolean = false) extends PersistenceMessages

  case class Delete(name: String, `type`: Class[_ <: Artifact]) extends PersistenceMessages

  case class ReadExpanded(name: String, `type`: Class[_ <: Artifact]) extends PersistenceMessages

}

trait PersistenceActor extends ReplyActor with CommonSupportForActors with PersistenceNotificationProvider {

  import PersistenceActor._

  lazy implicit val timeout = PersistenceActor.timeout

  override protected def requestType: Class[_] = classOf[PersistenceMessages]

  override protected def errorRequest(request: Any): RequestError = UnsupportedPersistenceRequest(request)

  protected def info(): Any

  protected def all(`type`: Class[_ <: Artifact]): List[Artifact]

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): ArtifactResponseEnvelope

  protected def create(artifact: Artifact, source: Option[String], ignoreIfExists: Boolean): Artifact

  protected def read(name: String, `type`: Class[_ <: Artifact]): Option[Artifact]

  protected def update(artifact: Artifact, source: Option[String], create: Boolean): Artifact

  protected def delete(name: String, `type`: Class[_ <: Artifact]): Artifact

  final def reply(request: Any): Any = try {
    log.debug(s"${getClass.getSimpleName}: ${request.getClass.getSimpleName}")
    respond(request)
  } catch {
    case e: NotificationErrorException => e
    case e: Throwable => exception(PersistenceOperationFailure(e))
  }

  protected def respond(request: Any) = request match {

    case Start => start()

    case Shutdown => shutdown()

    case InfoRequest => info()

    case All(ofType) => all(ofType)

    case AllPaginated(ofType, page, perPage) => all(ofType, page, perPage)

    case Create(artifact, source, ignoreIfExists) => create(artifact, source, ignoreIfExists)

    case Read(name, ofType) => read(name, ofType)

    case ReadExpanded(name, ofType) => readExpanded(name, ofType)

    case Update(artifact, source, create) => update(artifact, source, create)

    case Delete(name, ofType) => delete(name, ofType)

    case _ => error(errorRequest(request))
  }

  protected def start() = {}

  protected def shutdown() = {}

  protected def readExpanded(name: String, ofType: Class[_ <: Artifact]): Option[Artifact] =
    read(name, ofType) match {
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
          case Some(sla: SlaReference) => read(sla.name, classOf[GenericSla]) match {
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
          case Some(routing: RoutingReference) => readExpanded(routing.name, classOf[DefaultRouting]) match {
            case Some(slaDefault: DefaultRouting) => Some(slaDefault)
            case _ => throw exception(ArtifactNotFound(routing.name, classOf[DefaultRouting]))
          }
          case _ => None
        },
        scale = service.scale match {
          case Some(scale: DefaultScale) => Some(scale)
          case Some(scale: ScaleReference) => readExpanded(scale.name, classOf[DefaultScale]) match {
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
    case breedReference: BreedReference => readExpanded(breedReference.name, classOf[DefaultBreed]) match {
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
        read(referencedArtifact.name, classOf[DefaultFilter]) match {
          case Some(defaultArtifact: DefaultFilter) => defaultArtifact
          case _ => throw exception(ArtifactNotFound(referencedArtifact.name, classOf[DefaultFilter]))
        }
      case defaultArtifact: DefaultFilter => defaultArtifact
    }

  private def expandEscalations(list: List[Escalation]): List[GenericEscalation] =
    list.map {
      case referencedArtifact: EscalationReference =>
        read(referencedArtifact.name, classOf[GenericEscalation]) match {
          case Some(defaultArtifact: GenericEscalation) => defaultArtifact
          case _ => throw exception(ArtifactNotFound(referencedArtifact.name, classOf[GenericEscalation]))
        }
      case defaultArtifact: GenericEscalation => defaultArtifact
    }
}

