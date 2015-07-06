package io.vamp.core.model.serialization

import io.vamp.core.model.workflow._
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer

object WorkflowSerializationFormat extends io.vamp.common.json.SerializationFormat {
  override def customSerializers = super.customSerializers :+
    new ScheduledWorkflowSerializer()
}

class ScheduledWorkflowSerializer() extends ArtifactSerializer[ScheduledWorkflow] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case scheduledWorkflow: ScheduledWorkflow =>
      val list = new ArrayBuffer[JField]
      list += JField("name", JString(scheduledWorkflow.name))

      scheduledWorkflow.trigger match {
        case DeploymentTrigger(deployment) => list += JField("deployment", JString(deployment))
        case TimeTrigger(time) => list += JField("time", JString(time))
        case EventTrigger(tags) => list += JField("tags", Extraction.decompose(tags))
        case _ =>
      }

      scheduledWorkflow.workflow match {
        case WorkflowReference(reference) => list += JField("workflow", JString(reference))
        case DefaultWorkflow(_, dependencies, requires, script) =>
          list += JField("import", Extraction.decompose(dependencies))
          list += JField("script", JString(script))
        case _ =>
      }

      list += JField("storage", Extraction.decompose(scheduledWorkflow.storage))

      new JObject(list.toList)
  }
}

