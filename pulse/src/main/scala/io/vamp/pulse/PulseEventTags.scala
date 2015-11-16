package io.vamp.pulse

import io.vamp.model.event.Event

object PulseEventTags {

  object DeploymentSynchronization {

    val updateTag = s"synchronization${Event.tagDelimiter}update"

    val deleteTag = s"synchronization${Event.tagDelimiter}delete"
  }

}
