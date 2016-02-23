package io.vamp.persistence.db

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.IoC._
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._
import io.vamp.model.event.{ Aggregator, EventQuery, LongValueAggregationResult }
import io.vamp.model.workflow.{ ScheduledWorkflow, Workflow }
import io.vamp.pulse.{ EventRequestEnvelope, PulseActor }

import scala.concurrent.Future

trait PersistenceStats extends ArtifactPaginationSupport {
  this: ActorSystemProvider with ExecutionContextProvider with NotificationProvider ⇒

  protected implicit def timeout: Timeout

  protected def count(`type`: Class[_ <: Artifact]): Future[Long] = all(`type`, 1, 1).map(_.total)

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): Future[ArtifactResponseEnvelope]

  protected def stats(): Future[Map[String, Any]] = {

    val types = List(
      classOf[Gateway],
      classOf[Breed],
      classOf[Blueprint],
      classOf[Sla],
      classOf[Scale],
      classOf[Escalation],
      classOf[Route],
      classOf[Filter],
      classOf[Rewrite],
      classOf[Workflow],
      classOf[ScheduledWorkflow]
    )

    for {
      map ← Future.sequence(types.flatMap(artifact)).map(list ⇒ list.reduce((m1, m2) ⇒ m1 ++ m2))
      deployments ← allArtifacts[Deployment]
    } yield {
      val clusters = deployments.flatMap(_.clusters)
      val services = clusters.flatMap(_.services)
      map + ("deployments" -> Map("count" -> deployments.size, "clusters" -> clusters.size, "services" -> services.size))
    }
  }

  private def artifact(`type`: Class[_ <: Artifact]): Option[Future[Map[String, _]]] = {
    PersistenceArchive.tagFor(`type`).map {
      case tag ⇒
        def count(archiveTag: String): Future[Long] = {
          (actorFor[PulseActor] ? PulseActor.Query(EventRequestEnvelope(EventQuery(Set(tag, archiveTag), None, Some(Aggregator(Aggregator.count))), 1, 1))) map {
            case LongValueAggregationResult(count) ⇒ count
            case _                                 ⇒ 0
          }
        }
        for {
          currentCount ← all(`type`, 1, 1).map(_.total)
          created ← count(PersistenceArchive.archiveCreateTag)
          updated ← count(PersistenceArchive.archiveUpdateTag)
          deleted ← count(PersistenceArchive.archiveDeleteTag)
        } yield Map(
          tag -> Map("count" -> currentCount, "created" -> created, "updated" -> updated, "deleted" -> deleted)
        )
    }
  }
}
