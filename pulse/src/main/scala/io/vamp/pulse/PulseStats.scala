package io.vamp.pulse

import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.model.event.{ EventQuery, LongValueAggregationResult }
import io.vamp.model.notification.{ DeEscalate, Escalate }

import scala.concurrent.Future

trait PulseStats {
  this: ExecutionContextProvider ⇒

  protected def stats: Future[Map[String, Any]] = {
    for {
      escalations ← count(Escalate.tags)
      deescalations ← count(DeEscalate.tags)
      deployed ← count(Set(PulseEventTags.DeploymentSynchronization.deployedTag))
      redeploy ← count(Set(PulseEventTags.DeploymentSynchronization.redeployTag))
      undeployed ← count(Set(PulseEventTags.DeploymentSynchronization.undeployedTag))
    } yield Map(
      "escalation-count" -> escalations,
      "de-escalation-count" -> deescalations,
      "deployed-services" -> deployed,
      "redeployed-services" -> redeploy,
      "undeployed-services" -> undeployed
    )
  }

  private def count(tags: Set[String]): Future[Long] = {
    countEvents(EventQuery(tags, None)).map {
      case LongValueAggregationResult(value) ⇒ value
      case _                                 ⇒ 0
    }
  }

  protected def countEvents(eventQuery: EventQuery): Future[Any]
}
