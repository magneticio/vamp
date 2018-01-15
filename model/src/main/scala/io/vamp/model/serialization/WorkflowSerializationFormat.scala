package io.vamp.model.serialization

import java.time.format.DateTimeFormatter._

import io.vamp.common.RootAnyMap
import io.vamp.model.artifact.TimeSchedule.RepeatCount
import io.vamp.model.artifact._
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer

object WorkflowSerializationFormat extends io.vamp.common.json.SerializationFormat {
  override def customSerializers = super.customSerializers :+
    new WorkflowSerializer()
}

class WorkflowSerializer
    extends ArtifactSerializer[Workflow]
    with ReferenceSerialization
    with ArgumentListSerializer
    with HealthCheckSerializer
    with TraitDecomposer
    with DialectSerializer {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case workflow: Workflow ⇒
      val list = new ArrayBuffer[JField]
      list += JField("name", JString(workflow.name))
      list += JField("kind", JString(workflow.kind))
      list += JField("metadata", RootAnyMap.toJson(workflow.metadata))
      list += JField("breed", new JObject(JField("reference", JString(workflow.breed.name)) :: Nil))
      list += JField("status", JString(workflow.status.toString))

      workflow.schedule match {
        case TimeSchedule(period, repeatTimes, start) ⇒
          val time = new ArrayBuffer[JField]
          time += JField("period", JString(period.format))
          repeatTimes match {
            case RepeatCount(count) ⇒ time += JField("repeat", JInt(count))
            case _                  ⇒
          }
          start.foreach(start ⇒ time += JField("start", JString(start.format(ISO_OFFSET_DATE_TIME))))
          list += JField("schedule", new JObject(JField("time", new JObject(time.toList)) :: Nil))

        case EventSchedule(tags) ⇒
          val tagList = JField("tags", Extraction.decompose(tags)) :: Nil
          val event = JField("event", new JObject(tagList)) :: Nil
          list += JField("schedule", new JObject(event))

        case DaemonSchedule ⇒
          list += JField("schedule", JString("daemon"))

        case _ ⇒
      }

      if (workflow.environmentVariables.nonEmpty) list += JField("environment_variables", traits(workflow.environmentVariables.asInstanceOf[List[Trait]]))
      if (workflow.scale.isDefined) list += JField("scale", Extraction.decompose(workflow.scale.get))
      if (workflow.network.isDefined) list += JField("network", Extraction.decompose(workflow.network.get))
      if (workflow.arguments.nonEmpty) list += JField("arguments", serializeArguments(workflow.arguments))
      if (workflow.instances.nonEmpty) list += JField("instances", Extraction.decompose(workflow.instances))
      if (workflow.dialects.rootMap.nonEmpty) list += JField("dialects", serializeDialects(workflow.dialects))

      // Add optional values
      workflow.health.foreach(h ⇒ list += JField("health", Extraction.decompose(h)))
      workflow.healthChecks.foreach(hcs ⇒ list += JField("health_checks", serializeHealthChecks(hcs)))

      new JObject(list.toList)
  }
}
