package io.vamp.core.operation.workflow

import com.typesafe.config.ConfigFactory

object WorkflowConfiguration {
  lazy val enabled = ConfigFactory.load().getBoolean("vamp.core.operation.workflow.enabled")
}
