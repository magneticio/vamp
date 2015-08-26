package io.vamp.core.model.serialization

import java.time.format.DateTimeFormatter

import io.vamp.core.model.artifact.DeploymentService._
import io.vamp.core.model.artifact._
import io.vamp.core.model.notification.ModelNotificationProvider
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer

object DeploymentSerializationFormat extends io.vamp.common.json.SerializationFormat {
  override def customSerializers = super.customSerializers :+
    new DeploymentSerializer(full = false) :+
    new DeploymentServiceStateSerializer()
}

object FullDeploymentSerializationFormat extends io.vamp.common.json.SerializationFormat {
  override def customSerializers = super.customSerializers :+
    new DeploymentSerializer(full = true) :+
    new DeploymentServiceStateSerializer()
}

class DeploymentSerializer(full: Boolean) extends ArtifactSerializer[Deployment] with TraitDecomposer {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case deployment: Deployment ⇒
      val list = new ArrayBuffer[JField]
      list += JField("name", JString(deployment.name))
      list += JField("endpoints", traits(deployment.endpoints))
      list += JField("clusters", Extraction.decompose(deployment.clusters.map(cluster ⇒ cluster.name -> cluster).toMap))
      list += JField("ports", traits(deployment.ports))

      if (full) list += JField("environment_variables", Extraction.decompose(deployment.environmentVariables))
      else list += JField("environment_variables", traits(deployment.environmentVariables, alias = false))

      list += JField("hosts", traits(deployment.hosts))
      new JObject(list.toList)
  }
}

class DeploymentServiceStateSerializer extends ArtifactSerializer[DeploymentService.State] with ModelNotificationProvider {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case state: Error ⇒
      JObject(JField("name", JString(state.getClass.getSimpleName)), JField("started_at", JString(state.startedAt.format(DateTimeFormatter.ISO_DATE_TIME))), JField("notification", JString(message(state.notification))))

    case state: DeploymentService.State ⇒
      JObject(JField("name", JString(state.getClass.getSimpleName)), JField("started_at", JString(state.startedAt.format(DateTimeFormatter.ISO_DATE_TIME))))
  }
}

