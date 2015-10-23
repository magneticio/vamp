package io.vamp.operation.workflow

import com.typesafe.config.ConfigFactory

object WorkflowConfiguration {
  lazy val enabled = ConfigFactory.load().getBoolean("vamp.operation.workflow.enabled")
}
