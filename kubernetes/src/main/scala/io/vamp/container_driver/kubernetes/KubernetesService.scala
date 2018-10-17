package io.vamp.container_driver.kubernetes

import com.google.gson.reflect.TypeToken
import io.kubernetes.client.models._
import io.vamp.common.akka.CommonActorLogging
import io.vamp.common.util.HashUtil
import io.vamp.container_driver.ContainerDriver

import scala.collection.JavaConverters._
import scala.util.Try

case class KubernetesServicePort(name: String, protocol: String, port: Int)

object KubernetesServiceType extends Enumeration {
  val NodePort, LoadBalancer = Value
}

trait KubernetesService extends KubernetesArtifact {
  this: KubernetesContainerDriver with CommonActorLogging ⇒

  private val timeout = 3
  private val nameMatcher = """^[a-z]([-a-z0-9]*[a-z0-9])?$""".r

  protected def servicesForAllNamespaces(): Seq[V1Service] = {
    k8sClient.cache.readAllWithCache(
      K8sCache.services,
      "*",
      () ⇒ Try(k8sClient.coreV1Api.listServiceForAllNamespaces(null, null, false, null, null, null, null, timeout, false).getItems.asScala).toOption.getOrElse(Nil)
    )
  }

  protected def services(labels: Map[String, String] = Map()): Seq[V1Service] = {
    val selector = if (labels.isEmpty) null else labelSelector(labels)
    k8sClient.cache.readAllWithCache(
      K8sCache.services,
      selector,
      () ⇒ Try(k8sClient.coreV1Api.listNamespacedService(namespace.name, null, null, null, false, selector, null, null, timeout, false).getItems.asScala).toOption.getOrElse(Nil)
    )
  }

  protected def createService(name: String, `type`: KubernetesServiceType.Value, selector: String, ports: List[KubernetesServicePort], update: Boolean, labels: Map[String, String] = Map()): Unit = {
    val id = toId(name)

    def request(): V1Service = {
      val request = new V1Service
      val metadata = new V1ObjectMeta
      request.setMetadata(metadata)
      metadata.setName(id)
      metadata.setLabels(filterLabels(labels).asJava)

      val spec = new V1ServiceSpec
      request.setSpec(spec)
      spec.setSelector(Map(ContainerDriver.labelNamespace() → selector).asJava)
      spec.setType(`type`.toString)
      spec.setPorts(ports.map { p ⇒
        val port = new V1ServicePort
        port.setName(p.name)
        port.setProtocol(p.protocol.toUpperCase)
        port.setPort(p.port)
        port
      }.asJava)
      request
    }

    k8sClient.cache.readWithCache(
      K8sCache.services,
      id,
      () ⇒ k8sClient.coreV1Api.readNamespacedServiceStatus(id, namespace.name, null)
    ) match {
        case Some(_) ⇒
          if (update) {
            log.info(s"Updating service: $name")
            k8sClient.cache.writeWithCache(
              K8sCache.update,
              K8sCache.services,
              id,
              () ⇒ k8sClient.coreV1Api.patchNamespacedService(id, namespace.name, k8sClient.coreV1Api.getApiClient.getJSON.serialize(request()), null)
            )
          }
          else log.debug(s"Service exists: $name")

        case None ⇒
          log.info(s"Creating service: $name")
          k8sClient.cache.writeWithCache(
            K8sCache.create,
            K8sCache.services,
            id,
            () ⇒ k8sClient.coreV1Api.createNamespacedService(namespace.name, request(), null)
          )
      }
  }

  protected def deleteService(name: String): Unit = {
    log.info(s"Deleting service: $name")
    k8sClient.cache.writeWithCache(
      K8sCache.delete,
      K8sCache.services,
      name,
      () ⇒ k8sClient.coreV1Api.deleteNamespacedService(name, namespace.name, null, null, null, false, null)
    )
  }

  protected def createService(request: String): Unit = {
    log.info(s"Creating service")
    k8sClient.coreV1Api.createNamespacedService(
      namespace.name,
      k8sClient.coreV1Api.getApiClient.getJSON.deserialize(request, new TypeToken[V1Service]() {}.getType),
      null
    )
  }

  private def toId(name: String): String = name match {
    case nameMatcher(_*) if name.length < 25 ⇒ name
    case _                                   ⇒ s"hex${HashUtil.hexSha1(name).substring(0, 20)}"
  }
}
