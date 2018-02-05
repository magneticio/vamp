package io.vamp.container_driver.kubernetes

import io.vamp.common.akka.CommonActorLogging

import scala.concurrent.Future

case class CreateNamespace(name: String)

trait KubernetesNamespace extends KubernetesArtifact {
  this: KubernetesContainerDriver with CommonActorLogging ⇒

  private lazy val url = s"$apiUrl/api/v1/namespaces"

  protected def createNamespace(ns: CreateNamespace): Future[Any] = {
    val request =
      s"""
         |{
         |  "kind": "Namespace",
         |  "apiVersion": "v1",
         |  "metadata": {
         |    "name": "${ns.name}",
         |    "creationTimestamp": null
         |  },
         |  "spec": {},
         |  "status": {}
         |}
      """.stripMargin

    retrieve(url, ns.name,
      () ⇒ {
        log.debug(s"Namespace exists: ${ns.name}")
        Future.successful(false)
      },
      () ⇒ {
        log.info(s"Creating namespace: ${ns.name}")
        httpClient.post[Any](url, request, apiHeaders)
      })
  }
}
