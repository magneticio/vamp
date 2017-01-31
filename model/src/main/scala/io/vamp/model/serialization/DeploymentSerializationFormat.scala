package io.vamp.model.serialization

import java.time.format.DateTimeFormatter

import io.vamp.model.artifact.DeploymentService.Status.Phase.Failed
import io.vamp.model.artifact._
import io.vamp.model.notification.ModelNotificationProvider
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer

object DeploymentSerializationFormat extends io.vamp.common.json.SerializationFormat {
  override def customSerializers = super.customSerializers :+
    new DeploymentSerializer(full = false) :+
    new DeploymentServiceStatusSerializer() :+
    new DeploymentServiceStatusPhaseSerializer()
}

object FullDeploymentSerializationFormat extends io.vamp.common.json.SerializationFormat {
  override def customSerializers = super.customSerializers :+
    new DeploymentSerializer(full = true) :+
    new DeploymentServiceStatusSerializer() :+
    new DeploymentServiceStatusPhaseSerializer()
}

class DeploymentSerializer(full: Boolean) extends ArtifactSerializer[Deployment] with TraitDecomposer with BlueprintGatewaySerializer {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case deployment: Deployment ⇒
      val list = new ArrayBuffer[JField]
      list += JField("name", JString(deployment.name))
      list += JField("kind", JString(deployment.kind))
      list += JField("metadata", Extraction.decompose(deployment.metadata))
      list += JField(Lookup.entry, JString(deployment.lookupName))
      list += JField("clusters", Extraction.decompose(deployment.clusters.map(cluster ⇒ cluster.name → cluster).toMap))
      list += JField("ports", traits(deployment.ports))

      if (full) list += JField("environment_variables", Extraction.decompose(deployment.environmentVariables))
      else list += JField("environment_variables", traits(deployment.environmentVariables, alias = false))

      list += JField("hosts", traits(deployment.hosts))
      new JObject(list.toList)
  }
}

class DeploymentServiceStatusSerializer extends ArtifactSerializer[DeploymentService.Status] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case status: DeploymentService.Status ⇒
      val list = new ArrayBuffer[JField]

      list += JField("intention", JString(status.intention.toString))
      list += JField("since", JString(status.since.format(DateTimeFormatter.ISO_DATE_TIME)))
      list += JField("phase", Extraction.decompose(status.phase))

      new JObject(list.toList)
  }
}

class DeploymentServiceStatusPhaseSerializer extends ArtifactSerializer[DeploymentService.Status.Phase] with ModelNotificationProvider {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case step: DeploymentService.Status.Phase ⇒

      val list = new ArrayBuffer[JField]

      list += JField("name", JString(step.name))
      list += JField("since", JString(step.since.format(DateTimeFormatter.ISO_DATE_TIME)))

      step match {
        case failure: Failed ⇒ list += JField("notification", JString(message(failure.notification)))
        case _               ⇒
      }

      new JObject(list.toList)
  }
}
