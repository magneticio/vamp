package io.vamp.core.model.serialization

import java.time.format.DateTimeFormatter

import io.vamp.core.model.artifact.DeploymentService.State.Step.Failure
import io.vamp.core.model.artifact._
import io.vamp.core.model.notification.ModelNotificationProvider
import org.json4s.JsonAST.JString
import org.json4s._

import scala.collection.mutable.ArrayBuffer

object DeploymentSerializationFormat extends io.vamp.common.json.SerializationFormat {
  override def customSerializers = super.customSerializers :+
    new DeploymentSerializer(full = false) :+
    new DeploymentServiceStateSerializer() :+
    new DeploymentServiceStateStepSerializer()
}

object FullDeploymentSerializationFormat extends io.vamp.common.json.SerializationFormat {
  override def customSerializers = super.customSerializers :+
    new DeploymentSerializer(full = true) :+
    new DeploymentServiceStateSerializer() :+
    new DeploymentServiceStateStepSerializer()
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

class DeploymentServiceStateSerializer extends ArtifactSerializer[DeploymentService.State] {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case state: DeploymentService.State ⇒
      val list = new ArrayBuffer[JField]

      list += JField("intention", JString(state.intention.toString))
      list += JField("since", JString(state.since.format(DateTimeFormatter.ISO_DATE_TIME)))
      list += JField("step", Extraction.decompose(state.step))

      new JObject(list.toList)
  }
}

class DeploymentServiceStateStepSerializer extends ArtifactSerializer[DeploymentService.State.Step] with ModelNotificationProvider {
  override def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case step: DeploymentService.State.Step ⇒

      val list = new ArrayBuffer[JField]

      list += JField("name", JString(step.name))
      list += JField("since", JString(step.since.format(DateTimeFormatter.ISO_DATE_TIME)))

      step match {
        case failure: Failure ⇒ list += JField("notification", JString(message(failure.notification)))
        case _                ⇒
      }

      new JObject(list.toList)
  }
}
