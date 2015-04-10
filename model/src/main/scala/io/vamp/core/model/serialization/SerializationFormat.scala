package io.vamp.core.model.serialization

import io.vamp.common.json.{MapSerializer, OffsetDateTimeSerializer, SnakeCaseSerializationFormat}
import org.json4s._

object SerializationFormat {
  def apply(formats: io.vamp.common.json.SerializationFormat*) = formats.foldLeft(DefaultFormats: Formats)((f1, f2) => {
    val serializers = f2.customSerializers.foldLeft(f1)((s1, s2) => s1 + s2)
    val keySerializers = f2.customKeySerializers.foldLeft(serializers)((s1, s2) => s1 + s2)
    f2.fieldSerializers.foldLeft(keySerializers)((s1, s2) => s1 + s2)
  })

  val default = SerializationFormat(OffsetDateTimeSerializer, SnakeCaseSerializationFormat, MapSerializer, BreedSerializationFormat, BlueprintSerializationFormat, SlaSerializationFormat, DeploymentSerializationFormat)
}

abstract class ArtifactSerializer[A: Manifest] extends Serializer[A] {
  override final def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), A] = throw new UnsupportedOperationException()
}

abstract class ArtifactKeySerializer[A: Manifest] extends KeySerializer[A] {
  override final def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, String), A] = throw new UnsupportedOperationException()
}

abstract class ArtifactFieldSerializer[A: Manifest] extends FieldSerializer[A]

