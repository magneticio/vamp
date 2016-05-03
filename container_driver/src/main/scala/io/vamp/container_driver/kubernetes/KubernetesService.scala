package io.vamp.container_driver.kubernetes

import akka.actor.ActorLogging
import io.vamp.common.http.RestClient
import io.vamp.container_driver.Docker

import scala.concurrent.Future

trait KubernetesService {
  this: KubernetesContainerDriver with ActorLogging ⇒

  protected def createService(name: String, docker: Docker): Future[Any] = {
    val url = s"$kubernetesUrl/api/v1/namespaces/default/services"
    val request =
      s"""
         |{
         |  "kind": "Service",
         |  "apiVersion": "v1",
         |  "metadata": {
         |    "name": "$name"
         |  },
         |  "spec": {
         |    "selector": {
         |      "app": "$name"
         |    },
         |    "ports": [${docker.portMappings.map(pm ⇒ s"""{"name": "p${pm.containerPort}", "protocol": "${pm.protocol.toUpperCase}", "port": ${pm.hostPort}, "targetPort": ${pm.containerPort}}""").mkString(", ")}],
         |    "type": "NodePort"
         |  }
         |}
   """.stripMargin

    retrieve(url, name,
      () ⇒ {
        log.debug(s"Service exists: $name")
      },
      () ⇒ {
        log.info(s"Creating service: $name")
        RestClient.post[Any](url, request)
      }
    )
  }
}
