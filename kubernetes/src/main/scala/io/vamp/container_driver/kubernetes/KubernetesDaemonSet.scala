package io.vamp.container_driver.kubernetes

import com.google.gson.reflect.TypeToken
import io.kubernetes.client.models._
import io.vamp.common.akka.CommonActorLogging
import io.vamp.container_driver.{ ContainerDriver, Docker }

import scala.collection.JavaConverters._

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

  protected def createDaemonSet(ds: DaemonSet, labels: Map[String, String] = Map()): Unit = {
    k8sClient.cache.readRequestWithCache(
      K8sCache.daemonSets,
      ds.name,
      () ⇒ k8sClient.extensionsV1beta1Api.readNamespacedDaemonSetStatus(ds.name, namespace.name, null)
    ) match {
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

          k8sClient.cache.writeRequestWithCache(
            K8sCache.daemonSets,
            ds.name,
            () ⇒ k8sClient.extensionsV1beta1Api.createNamespacedDaemonSet(ds.name, request, null)
          )
      }
  }

  protected def createDaemonSet(request: String): Unit = {
    log.info(s"Creating daemon set")
    k8sClient.extensionsV1beta1Api.createNamespacedDaemonSet(
      namespace.name,
      k8sClient.extensionsV1beta1Api.getApiClient.getJSON.deserialize(request, new TypeToken[V1beta1DaemonSet]() {}.getType),
      null
    )
  }

  protected def deleteDaemonSetById(id: String): Unit = {
    log.info(s"Deleting daemon set $id")
    k8sClient.cache.writeRequestWithCache(
      K8sCache.daemonSets,
      id,
      () ⇒ k8sClient.extensionsV1beta1Api.deleteNamespacedDaemonSet(id, namespace.name, new V1DeleteOptions().propagationPolicy("Background"), null, null, null, null)
    )
  }
}
