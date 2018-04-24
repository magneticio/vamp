package io.vamp.container_driver.kubernetes

import io.kubernetes.client.models.V1Node
import io.vamp.common.akka.CommonActorLogging

import scala.collection.JavaConverters._
import scala.util.Try

trait KubernetesNode extends KubernetesArtifact {
  this: KubernetesContainerDriver with CommonActorLogging ⇒

  protected def nodes: Seq[V1Node] = {
    k8sClient.cache.readAllWithCache(
      K8sCache.nodes,
      "*",
      () ⇒ Try(k8sClient.coreV1Api.listNode(null, null, null, null, null, null).getItems.asScala).toOption.getOrElse(Nil)
    )
  }
}
