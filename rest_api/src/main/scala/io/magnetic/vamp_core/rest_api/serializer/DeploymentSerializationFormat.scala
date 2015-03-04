package io.magnetic.vamp_core.rest_api.serializer

import io.magnetic.vamp_core.model.artifact.{Deployment, DeploymentState, Trait}
import org.json4s.JsonAST.JString
import org.json4s._

object DeploymentSerializationFormat extends ArtifactSerializationFormat {
  override def customSerializers: List[ArtifactSerializer[_]] = super.customSerializers :+
    new DeploymentStateSerializer()

  override def fieldSerializers: List[ArtifactFieldSerializer[_]] = super.fieldSerializers :+
    new DeploymentStateFieldSerializer()
}

class DeploymentStateSerializer extends ArtifactSerializer[Deployment.State.Value] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case direction: Deployment.State.Value => JString(direction.toString)
  }
}

class DeploymentStateFieldSerializer extends ArtifactFieldSerializer[DeploymentState] {
  //override val serializer: PartialFunction[(String, Any), Option[(String, Any)]] = ignore("state")
}