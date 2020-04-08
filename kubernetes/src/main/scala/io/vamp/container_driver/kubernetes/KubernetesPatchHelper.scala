package io.vamp.container_driver.kubernetes

import java.util

import io.kubernetes.client.openapi.{ApiClient, Pair}
import okhttp3.{MediaType, Request, RequestBody}

import scala.util.parsing.json.JSON
import scala.collection.JavaConverters._

class CC[T] {def unapply(a: Any): Option[T] = Some(a.asInstanceOf[T])}

object M extends CC[Map[String, Any]]

object S extends CC[String]

object KubernetesPatchHelper {

  def prepareDaemonSetPatchRequest(body: String, apiClient: ApiClient, customNamespace: String): Request = {
    val name = findName(body)
    val path = "/apis/extensions/v1beta1/namespaces/{namespace}/daemonsets/{name}".replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name.toString)).replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(customNamespace.toString))

    buildRequest(body, apiClient, path)
  }

  def prepareDeploymentPatchRequest(body: String, apiClient: ApiClient, customNamespace: String): Request = {
    val name = findName(body)
    val path: String = "/apis/extensions/v1beta1/namespaces/{namespace}/deployments/{name}".replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name.toString)).replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(customNamespace.toString))

    buildRequest(body, apiClient, path)
  }

  def prepareServicePatchRequest(body: String, apiClient: ApiClient, customNamespace: String): Request = {
    val name = findName(body)
    val path = "/api/v1/namespaces/{namespace}/services/{name}".replaceAll("\\{" + "name" + "\\}", apiClient.escapeString(name.toString)).replaceAll("\\{" + "namespace" + "\\}", apiClient.escapeString(customNamespace.toString))

    buildRequest(body, apiClient, path)
  }

  private def buildRequest(request: String, apiClient: ApiClient, localVarPath: String): Request = {
    val localVarQueryParams = new util.ArrayList[Pair]
    val localVarCollectionQueryParams = new util.ArrayList[Pair]
    val localVarHeaderParams = prepareHeaderParams

    val builder = new Request.Builder()

    apiClient.updateParamsForAuth(Array[String]("BearerToken"), localVarQueryParams, localVarHeaderParams, Map.empty[String, String].asJava)
    apiClient.processHeaderParams(localVarHeaderParams, builder)

    builder
      .url(apiClient.buildUrl(localVarPath, localVarQueryParams, localVarCollectionQueryParams))
      .patch(RequestBody.create(MediaType.parse("application/merge-patch+json"), request))
      .build()
  }

  private def findName(request: String): String = {
    val result = for {
      Some(M(map)) ← List(JSON.parseFull(request))
      M(metadata) = map("metadata")
      S(name) = metadata("name")
    } yield {
      name
    }
    result.head
  }

  private def prepareHeaderParams: util.HashMap[String, String] = {
    val localVarHeaderParams = new util.HashMap[String, String]

    localVarHeaderParams.put("Accept", "application/json")
    localVarHeaderParams.put("Content-Type", "application/merge-patch+json")
    localVarHeaderParams
  }
}
