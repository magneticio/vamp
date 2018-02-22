package io.vamp.persistence

import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.Artifact
import io.vamp.common.akka.CommonProvider
import io.vamp.common.akka.IoC._
import io.vamp.model.artifact._
import io.vamp.model.event.{ Aggregator, EventQuery, LongValueAggregationResult }
import io.vamp.pulse.{ EventRequestEnvelope, PulseActor }

import scala.concurrent.Future

private case class DeploymentsStatistics(count: Int, clusters: Int, services: Int)

trait PersistenceStats extends ArtifactPaginationSupport with PersistenceTag {
  this: CommonProvider ⇒

  protected implicit def timeout: Timeout

  protected def all[T <: Artifact](`type`: Class[T], page: Int, perPage: Int, filter: (T) ⇒ Boolean = (_: T) ⇒ true): ArtifactResponseEnvelope

  protected def stats(): Future[Map[String, Any]] = {

    val types = List(
      classOf[Gateway],
      classOf[Breed],
      classOf[Blueprint],
      classOf[Sla],
      classOf[Scale],
      classOf[DeploymentServiceHealth],
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

  protected def artifact(`type`: Class[_ <: Artifact]): Option[Future[Map[String, _]]] = {
    tagFor(`type`).map { tag ⇒
      def count(archiveTag: String): Future[Long] = {
        (actorFor[PulseActor] ? PulseActor.Query(EventRequestEnvelope(EventQuery(Set(tag, archiveTag), None, None, Some(Aggregator(Aggregator.count))), 1, 1))) map {
          case LongValueAggregationResult(count) ⇒ count
          case _                                 ⇒ 0
        }
      }
      for {
        created ← count(PersistenceArchive.archiveCreateTag)
        updated ← count(PersistenceArchive.archiveUpdateTag)
        deleted ← count(PersistenceArchive.archiveDeleteTag)
      } yield Map(
        tag → Map("count" → all(`type`, 1, 1).total, "created" → created, "updated" → updated, "deleted" → deleted)
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
