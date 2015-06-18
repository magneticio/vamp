package io.vamp.core.model.serialization

import io.vamp.common.json._
import org.json4s._

object CoreSerializationFormat {

  private val common = List(
    OffsetDateTimeSerializer,
    SnakeCaseSerializationFormat,
    MapSerializer,
    BreedSerializationFormat,
    BlueprintSerializationFormat,
    SlaSerializationFormat,
    WorkflowSerializationFormat
  )

  val default: Formats = SerializationFormat(common :+ DeploymentSerializationFormat: _*)

  val full: Formats = SerializationFormat(common :+ FullDeploymentSerializationFormat: _*)
}

abstract class ArtifactSerializer[A: Manifest] extends Serializer[A] {
  override final def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), A] = throw new UnsupportedOperationException()
}

abstract class ArtifactKeySerializer[A: Manifest] extends KeySerializer[A] {
  override final def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, String), A] = throw new UnsupportedOperationException()
}

abstract class ArtifactFieldSerializer[A: Manifest] extends FieldSerializer[A]
