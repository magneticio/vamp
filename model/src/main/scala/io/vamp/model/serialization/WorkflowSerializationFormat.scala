package io.vamp.model.serialization

import java.time.format.DateTimeFormatter._

import io.vamp.model.workflow.TimeTrigger.RepeatTimesCount
import io.vamp.model.workflow._
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer

object WorkflowSerializationFormat extends io.vamp.common.json.SerializationFormat {
  override def customSerializers = super.customSerializers :+
    new ScheduledWorkflowSerializer()
}

class ScheduledWorkflowSerializer() extends ArtifactSerializer[ScheduledWorkflow] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case scheduledWorkflow: ScheduledWorkflow ⇒
      val list = new ArrayBuffer[JField]
      list += JField("name", JString(scheduledWorkflow.name))

      scheduledWorkflow.trigger match {
        case DeploymentTrigger(deployment) ⇒
          list += JField("deployment", JString(deployment))
        case TimeTrigger(period, repeatTimes, startTime) ⇒
          list += JField("period", JString(period.format))
          repeatTimes match {
            case RepeatTimesCount(count) ⇒ list += JField("repeatCount", JInt(count))
            case _                       ⇒
          }
          startTime.foreach(start ⇒ list += JField("startTime", JString(start.format(ISO_OFFSET_DATE_TIME))))
        case EventTrigger(tags) ⇒
          list += JField("tags", Extraction.decompose(tags))
        case _ ⇒
      }

      scheduledWorkflow.workflow match {
        case WorkflowReference(reference) ⇒ list += JField("workflow", JString(reference))
        case DefaultWorkflow(_, script)   ⇒ list += JField("script", JString(script))
        case _                            ⇒
      }

      new JObject(list.toList)
  }
}

