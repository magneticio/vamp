package io.vamp.persistence.db

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka.IoC._
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._
import io.vamp.model.event.{ Aggregator, EventQuery, LongValueAggregationResult }
import io.vamp.pulse.{ EventRequestEnvelope, PulseActor }

import scala.concurrent.Future

private case class DeploymentsStatistics(count: Int, clusters: Int, services: Int)

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
      classOf[Condition],
      classOf[Rewrite],
      classOf[Workflow]
    )

    for {
      map ← Future.sequence(types.flatMap(artifact)).map(list ⇒ list.reduce((m1, m2) ⇒ m1 ++ m2))
      deployments ← deployments()
    } yield {
      map + ("deployments" → Map("count" → deployments.count, "clusters" → deployments.clusters, "services" → deployments.services))
    }
  }

  private def artifact(`type`: Class[_ <: Artifact]): Option[Future[Map[String, _]]] = {
    PersistenceArchive.tagFor(`type`).map { tag ⇒
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
        tag → Map("count" → currentCount, "created" → created, "updated" → updated, "deleted" → deleted)
      )
    }
  }

  private def deployments(): Future[DeploymentsStatistics] = {

    def stats: Deployment ⇒ DeploymentsStatistics = {
      deployment ⇒ DeploymentsStatistics(1, deployment.clusters.size, deployment.clusters.flatMap(_.services).size)
    }

    def reduce: (DeploymentsStatistics, DeploymentsStatistics) ⇒ DeploymentsStatistics = {
      (s1, s2) ⇒ DeploymentsStatistics(s1.count + s2.count, s1.clusters + s2.clusters, s1.services + s2.services)
    }

    collectAll[Deployment, DeploymentsStatistics](allArtifacts[Deployment], {
      case deployments ⇒ deployments.map(stats).reduce(reduce)
    }).flatMap(stream ⇒ Future.sequence(stream.toList).map(_.reduce(reduce)))
  }
}
