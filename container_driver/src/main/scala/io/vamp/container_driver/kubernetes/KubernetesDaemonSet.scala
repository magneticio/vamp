package io.vamp.container_driver.kubernetes

import akka.actor.ActorLogging
import io.vamp.common.http.RestClient

import scala.concurrent.Future

trait KubernetesDaemonSet extends KubernetesArtifact {
  this: KubernetesContainerDriver with ActorLogging ⇒

  private lazy val url = s"$apiUrl/apis/extensions/v1beta1/namespaces/default/daemonsets"

  protected def createDaemonSet(ds: DaemonSet, labels: Map[String, String] = Map()): Future[Any] = {
    val request =
      s"""
         |{
         |  "apiVersion": "extensions/v1beta1",
         |  "kind": "DaemonSet",
         |  "metadata": {
         |    "name": "${ds.name}",
         |    ${labels2json(labels)}
         |  },
         |  "spec": {
         |    "template": {
         |      "metadata": {
         |        "labels": {
         |          "vamp": "${ds.name}"
         |        }
         |      },
         |      "spec": {
         |        "containers": [{
         |          "image": "${ds.docker.image}",
         |          "name": "${ds.name}",
         |          "ports": [${ds.docker.portMappings.map(pm ⇒ s"""{"containerPort": ${pm.containerPort}, "name": "p${pm.containerPort}"}""").mkString(", ")}],
         |          "command": [${ds.command.map(str ⇒ s""""$str"""").mkString(", ")}],
         |          "resources": {
         |            "request": {
         |              "cpu": ${ds.cpu},
         |              "mem": ${ds.mem}
         |            }
         |          }
         |        }]
         |      }
         |    }
         |  }
         |}
      """.stripMargin

    retrieve(url, ds.name,
      () ⇒ {
        log.debug(s"Daemon set exists: ${ds.name}")
        Future.successful(false)
      },
      () ⇒ {
        log.info(s"Creating daemon set: ${ds.name}")
        RestClient.post[Any](url, request, apiHeaders)
      }
    )
  }
}
