package io.vamp.persistence.operation

import io.vamp.model.artifact.{ Argument, Artifact }

case class WorkflowNetwork(name: String, network: String) extends Artifact

case class WorkflowArguments(name: String, arguments: List[Argument]) extends Artifact