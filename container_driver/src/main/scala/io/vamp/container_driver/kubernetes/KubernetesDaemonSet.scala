package io.vamp.container_driver.kubernetes

import akka.actor.ActorLogging
import io.vamp.common.http.RestClient

import scala.concurrent.Future

trait KubernetesDaemonSet {
  this: KubernetesContainerDriver with ActorLogging with KubernetesService ⇒

  protected def createDaemonSet(ds: DaemonSet): Future[Any] = {
    val url = s"$kubernetesUrl/apis/extensions/v1beta1/namespaces/default/daemonsets"
    val request =
      s"""
         |{
         |  "apiVersion": "extensions/v1beta1",
         |  "kind": "DaemonSet",
         |  "metadata": {
         |    "labels": {
         |      "name": "${ds.name}"
         |    },
         |    "name": "${ds.name}"
         |  },
         |  "spec": {
         |    "template": {
         |      "metadata": {
         |        "labels": {
         |          "app": "${ds.name}"
         |        }
         |      },
         |      "spec": {
         |        "containers": [{
         |          "image": "${ds.docker.image}",
         |          "name": "${ds.name}",
         |          "ports": [${ds.docker.portMappings.map(pm ⇒ s"""{"containerPort": ${pm.containerPort}, "name": "p${pm.containerPort}"}""").mkString(", ")}],
         |          "args": [${ds.args.map(str ⇒ s""""$str"""").mkString(", ")}],
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
      },
      () ⇒ {
        log.info(s"Creating daemon set: ${ds.name}")
        RestClient.post[Any](url, request)
      }
    ).flatMap(_ ⇒ createService(ds.name, ds.docker))
  }
}
