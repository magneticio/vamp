package io.vamp.pulse

import io.vamp.model.event.Event

object PulseEventTags {

  object Deployments {

    val deploymentEventType = "synchronization"

    val deployedTag = s"synchronization${Event.tagDelimiter}deployed"

    val redeployTag = s"synchronization${Event.tagDelimiter}redeploy"

    val undeployedTag = s"synchronization${Event.tagDelimiter}undeployed"
  }

  object Workflows {

    val scheduledTag = "scheduled"

    val unscheduledTag = "unscheduled"
  }
}
