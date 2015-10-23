package io.vamp.model.workflow

import io.vamp.model.artifact.{Artifact, Reference}

object Workflow {

  object Language extends Enumeration {
    val JavaScript = Value
  }

}

trait Workflow extends Artifact

case class WorkflowReference(name: String) extends Reference with Workflow

case class DefaultWorkflow(name: String, `import`: List[String], requires: List[String], script: String) extends Workflow {
  def language = Workflow.Language.JavaScript
}


trait Trigger

case class TimeTrigger(time: String) extends Trigger

case class DeploymentTrigger(deployment: String) extends Trigger

case class EventTrigger(tags: Set[String]) extends Trigger


case class ScheduledWorkflow(name: String, workflow: Workflow, trigger: Trigger, storage: Map[String, Any] = Map()) extends Artifact