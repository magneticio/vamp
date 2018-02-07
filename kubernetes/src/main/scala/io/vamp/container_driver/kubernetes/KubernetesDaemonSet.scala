package io.vamp.container_driver.kubernetes

import com.google.gson.reflect.TypeToken
import io.kubernetes.client.apis.ExtensionsV1beta1Api
import io.kubernetes.client.models._
import io.vamp.common.akka.CommonActorLogging
import io.vamp.container_driver.{ ContainerDriver, Docker }

import scala.collection.JavaConverters._
import scala.util.Try

case class DaemonSet(
  name:        String,
  docker:      Docker,
  cpu:         Double,
  mem:         Int,
  serviceType: Option[KubernetesServiceType.Value] = Option(KubernetesServiceType.NodePort),
  command:     List[String]                        = Nil
)

trait KubernetesDaemonSet extends KubernetesArtifact {
  this: KubernetesContainerDriver with CommonActorLogging ⇒

  private lazy val api = new ExtensionsV1beta1Api(k8sClient.api)

  protected def createDaemonSet(ds: DaemonSet, labels: Map[String, String] = Map()): Unit = {
    Try(api.readNamespacedDaemonSetStatus(ds.name, namespace.name, null)).toOption match {
      case Some(_) ⇒ log.debug(s"Daemon set exists: ${ds.name}")
      case None ⇒
        log.info(s"Creating daemon set: ${ds.name}")

        val request = new V1beta1DaemonSet

        val metadata = new V1ObjectMeta
        request.setMetadata(metadata)
        metadata.setName(ds.name)
        metadata.setLabels(filterLabels(labels + (ContainerDriver.withNamespace("name") → ds.name)).asJava)

        val spec = new V1beta1DaemonSetSpec
        request.setSpec(spec)
        val template = new V1PodTemplateSpec
        spec.setTemplate(template)

        val templateMetadata = new V1ObjectMeta
        templateMetadata.setLabels(filterLabels(Map(ContainerDriver.labelNamespace() → "daemon-set", ContainerDriver.withNamespace("daemon-set") → ds.name)).asJava)
        template.setMetadata(templateMetadata)

        val podSpec = new V1PodSpec
        template.setSpec(podSpec)

        val container = new V1Container
        podSpec.setContainers(List(container).asJava)

        container.setName(ds.name)
        container.setImage(ds.docker.image)
        container.setPorts(ds.docker.portMappings.map { pm ⇒
          val port = new V1ContainerPort
          port.setName(s"p${pm.containerPort}")
          port.setContainerPort(pm.containerPort)
          port
        }.asJava)
        container.setCommand(ds.command.asJava)

        val resources = new V1ResourceRequirements
        container.setResources(resources)
        resources.setRequests(Map("cpu" → ds.cpu.toString, "memory" → ds.mem.toString).asJava)

        api.createNamespacedDaemonSet(ds.name, request, null)
    }
  }

  protected def createDaemonSet(request: String): Unit = {
    log.info(s"Creating daemon set")
    api.createNamespacedDaemonSet(
      namespace.name,
      api.getApiClient.getJSON.deserialize(request, new TypeToken[V1beta1DaemonSet]() {}.getType),
      null
    )
  }
}
