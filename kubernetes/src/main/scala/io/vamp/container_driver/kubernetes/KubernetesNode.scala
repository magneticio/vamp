package io.vamp.container_driver.kubernetes

import io.kubernetes.client.openapi.models.V1Node
import io.vamp.common.akka.CommonActorLogging

import scala.collection.JavaConverters._
import scala.util.Try

trait KubernetesNode extends KubernetesArtifact {
  this: KubernetesContainerDriver with CommonActorLogging ⇒

  private val timeout = 3

  protected def nodes: Seq[V1Node] = {
    k8sClient.cache.readAllWithCache(
      K8sCache.nodes,
      "*",
      () ⇒ Try(k8sClient.coreV1Api.listNode(null, false, null, null, null, null, null, timeout, false).getItems.asScala).toOption.getOrElse(Nil)
    )
  }
}
