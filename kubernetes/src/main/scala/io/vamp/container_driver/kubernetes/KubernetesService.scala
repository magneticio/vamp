package io.vamp.container_driver.kubernetes


import java.util

import com.google.gson.reflect.TypeToken
import com.squareup.okhttp.{Call, MediaType, Request, RequestBody}
import io.kubernetes.client.models._
import io.kubernetes.client.{ApiClient, Pair}
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
      () ⇒ Try(k8sClient.coreV1Api.listNamespacedService(customNamespace, null, null, null, false, selector, null, null, timeout, false).getItems.asScala).toOption.getOrElse(Nil)
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
      () ⇒ k8sClient.coreV1Api.readNamespacedServiceStatus(id, customNamespace, null)
    ) match {
        case Some(_) ⇒
          if (update) {
            log.debug(s"Updating service: $name")
            k8sClient.cache.writeWithCache(
              K8sCache.update,
              K8sCache.services,
              id,
              () ⇒ k8sClient.coreV1Api.patchNamespacedService(id, customNamespace, k8sClient.coreV1Api.getApiClient.getJSON.serialize(request()), null)
            )
          }
          else log.debug(s"Service exists: $name")

        case None ⇒
          log.debug(s"Creating service: $name")
          k8sClient.cache.writeWithCache(
            K8sCache.create,
            K8sCache.services,
            id,
            () ⇒ k8sClient.coreV1Api.createNamespacedService(customNamespace, request(), null)
          )
      }
  }

  protected def deleteService(name: String): Unit = {
    log.debug(s"Deleting service: $name")
    import io.kubernetes.client.models.V1DeleteOptions
    val body = new V1DeleteOptions
    k8sClient.cache.writeWithCache(
      K8sCache.delete,
      K8sCache.services,
      name,
      () ⇒ k8sClient.coreV1Api.deleteNamespacedService(name, customNamespace, body, null, null, false, null)
    )
  }

  protected def createService(request: String): Unit = {
    log.debug(s"Creating service")
    k8sClient.coreV1Api.createNamespacedService(
      customNamespace,
      k8sClient.coreV1Api.getApiClient.getJSON.deserialize(request, new TypeToken[V1Service]() {}.getType),
      null
    )
  }

  protected def updateService(request: String): Unit = {
    log.debug(s"Updating Kubernetes service")
    log.info("Service Request: " + request)

    val serviceName = KubernetesPatchHelper.findName(request)
    val call = prepareServicePatchCall(request, k8sClient.extensionsV1beta1Api.getApiClient, serviceName)
    k8sClient.coreV1Api.getApiClient.execute(call)
  }


  private def prepareServicePatchCall(request: String, apiClient: ApiClient, name: String) : Call = {
    val localVarPath = "/api/v1/namespaces/{namespace}/services/{name}".replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name.toString)).replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(customNamespace.toString))

    val localVarQueryParams = new util.ArrayList[Pair]
    val localVarCollectionQueryParams = new util.ArrayList[Pair]
    val localVarHeaderParams = new util.HashMap[String, String]

    localVarHeaderParams.put("Accept", "application/json")
    localVarHeaderParams.put("Content-Type", "application/merge-patch+json")

    val localVarAuthNames = Array[String]("BearerToken")

    log.info("Request path: " + localVarPath)

    apiClient.updateParamsForAuth(localVarAuthNames, localVarQueryParams, localVarHeaderParams)

    val builder = new Request.Builder()

    apiClient.processHeaderParams(localVarHeaderParams, builder)
    val r = builder
      .url(apiClient.buildUrl(localVarPath, localVarQueryParams, localVarCollectionQueryParams))
      .patch(RequestBody.create(MediaType.parse("application/merge-patch+json"), request))
      .build()

    apiClient.getHttpClient.newCall(r)
  }

  private def toId(name: String): String = name match {
    case nameMatcher(_*) if name.length < 25 ⇒ name
    case _                                   ⇒ s"hex${HashUtil.hexSha1(name).substring(0, 20)}"
  }
}
