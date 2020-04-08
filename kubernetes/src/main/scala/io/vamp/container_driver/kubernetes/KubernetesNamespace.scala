package io.vamp.container_driver.kubernetes

import io.kubernetes.client.openapi.models.{ V1Namespace, V1ObjectMeta }
import io.vamp.common.{ Namespace, NamespaceProvider }
import io.vamp.common.akka.CommonActorLogging

import scala.util.Try

case class CreateNamespace(name: String)

trait KubernetesNamespace extends KubernetesArtifact {
  this: KubernetesContainerDriver with CommonActorLogging ⇒

  protected def createNamespace(ns: CreateNamespace): Unit = {
    /*
    This trait initialization may seem a bit odd but create namespace is not namespaced
    and it is better to keep customNamespace method in one place.
     */
    new NamespaceProvider {
      override implicit def namespace: Namespace = Namespace(ns.name)

      Try(k8sClient.coreV1Api.readNamespaceStatus(customNamespace, null)).toOption match {
        case Some(_) ⇒ log.debug(s"Namespace exists: ${customNamespace}")
        case None ⇒
          log.debug(s"Creating namespace: ${customNamespace}")
          val request = new V1Namespace
          val metadata = new V1ObjectMeta
          request.setMetadata(metadata)
          metadata.setName(customNamespace)
          k8sClient.coreV1Api.createNamespace(request, null, null, null)
      }

    }

  }
}
