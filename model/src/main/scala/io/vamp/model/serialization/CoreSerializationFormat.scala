package io.vamp.model.serialization

import io.vamp.common.json._
import org.json4s._

object CoreSerializationFormat {

  private val common = List(
    ThrowableSerializer(Some("Internal error.")),
    OffsetDateTimeSerializer,
    SnakeCaseSerializationFormat,
    MapSerializer,
    BreedSerializationFormat,
    BlueprintSerializationFormat,
    GatewaySerializationFormat,
    SlaSerializationFormat,
    WorkflowSerializationFormat,
    TemplateSerializationFormat
  )

  val default: Formats = SerializationFormat(common :+ DeploymentSerializationFormat: _*)

  val full: Formats = SerializationFormat(common :+ FullDeploymentSerializationFormat: _*)
}

abstract class ArtifactSerializer[A: Manifest] extends Serializer[A] {
  override final def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), A] = SerializationFormat.unsupported
}

abstract class ArtifactKeySerializer[A: Manifest] extends KeySerializer[A] {
  override final def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, String), A] = {
    case some ⇒ throw new UnsupportedOperationException(s"Cannot deserialize [${some.getClass}]: $some")
  }
}

abstract class ArtifactFieldSerializer[A: Manifest] extends FieldSerializer[A]
