package io.vamp.persistence.db

import akka.util.Timeout
import io.vamp.common.akka.{ ActorSystemProvider, ExecutionContextProvider }
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._
import io.vamp.model.workflow.{ ScheduledWorkflow, Workflow }

import scala.concurrent.Future

trait PersistenceStats extends ArtifactPaginationSupport {
  this: ActorSystemProvider with ExecutionContextProvider with NotificationProvider ⇒

  protected def stats(): Future[Map[String, Any]] = {
    for {
      map ← Future.sequence {
        List(
          count(classOf[Gateway]).map("gateways" -> _),
          count(classOf[Breed]).map("breeds" -> _),
          count(classOf[Blueprint]).map("blueprints" -> _),
          count(classOf[Sla]).map("slas" -> _),
          count(classOf[Scale]).map("scales" -> _),
          count(classOf[Escalation]).map("escalations" -> _),
          count(classOf[Route]).map("routes" -> _),
          count(classOf[Filter]).map("filters" -> _),
          count(classOf[Rewrite]).map("rewrites" -> _),
          count(classOf[Workflow]).map("workflows" -> _),
          count(classOf[ScheduledWorkflow]).map("scheduled-workflows" -> _)
        )
      } map (_.toMap)

      deployments ← allArtifacts[Deployment]

    } yield {

      val clusters = deployments.flatMap(_.clusters)
      val services = clusters.flatMap(_.services)

      map + ("deployments" -> deployments.size) + ("deployment-clusters" -> clusters.size) + ("deployment-cluster-services" -> services.size)
    }
  }

  protected implicit def timeout: Timeout

  protected def count(`type`: Class[_ <: Artifact]): Future[Long] = all(`type`, 1, 1).map(_.total)

  protected def all(`type`: Class[_ <: Artifact], page: Int, perPage: Int): Future[ArtifactResponseEnvelope]
}
