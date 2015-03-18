package io.magnetic.vamp_core.model.serialization

import java.time.format.DateTimeFormatter

import io.magnetic.vamp_core.model.artifact.DeploymentService
import io.magnetic.vamp_core.model.artifact.DeploymentService._
import io.magnetic.vamp_core.model.notification.ModelNotificationProvider
import org.json4s.JsonAST.JString
import org.json4s._

object DeploymentSerializationFormat extends ArtifactSerializationFormat {
  override def customSerializers: List[ArtifactSerializer[_]] = super.customSerializers :+
    new DeploymentServiceStateSerializer()
}

class DeploymentServiceStateSerializer extends ArtifactSerializer[DeploymentService.State] with ModelNotificationProvider {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case state: Error =>
      JObject(JField("name", JString(state.getClass.getSimpleName)), JField("startedAt", JString(state.startedAt.format(DateTimeFormatter.ISO_INSTANT))), JField("notification", JString(message(state.notification))))

    case state: DeploymentService.State =>
      JObject(JField("name", JString(state.getClass.getSimpleName)), JField("startedAt", JString(state.startedAt.format(DateTimeFormatter.ISO_INSTANT))))
  }
}

