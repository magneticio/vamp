package io.vamp.container_driver.kubernetes

import io.vamp.common.akka.CommonActorLogging
import io.vamp.container_driver.{ ContainerDriver, Docker }

import scala.concurrent.Future

case class DaemonSet(
  name:        String,
  docker:      Docker,
  cpu:         Double,
  mem:         Int,
  serviceType: Option[KubernetesServiceType.Value] = Option(KubernetesServiceType.NodePort),
  command:     List[String]                        = Nil
)

trait KubernetesDaemonSet extends KubernetesArtifact {
  this: KubernetesContainerDriver with CommonActorLogging ⇒

  private lazy val url = s"$apiUrl/apis/extensions/v1beta1/namespaces/${namespace.name}/daemonsets"

  protected def createDaemonSet(ds: DaemonSet, labels: Map[String, String] = Map()): Future[Any] = {
    val request =
      s"""
         |{
         |  "apiVersion": "extensions/v1beta1",
         |  "kind": "DaemonSet",
         |  "metadata": {
         |    "name": "${ds.name}",
         |    ${labels2json(labels + (ContainerDriver.withNamespace("name") → ds.name))}
         |  },
         |  "spec": {
         |    "template": {
         |      "metadata": {
         |        ${labels2json(Map(ContainerDriver.labelNamespace() → "daemon-set", ContainerDriver.withNamespace("daemon-set") → ds.name))}
         |      },
         |      "spec": {
         |        "containers": [{
         |          "image": "${ds.docker.image}",
         |          "name": "${ds.name}",
         |          "ports": [${ds.docker.portMappings.map(pm ⇒ s"""{"containerPort": ${pm.containerPort}, "name": "p${pm.containerPort}"}""").mkString(", ")}],
         |          "command": [${ds.command.map(str ⇒ s""""$str"""").mkString(", ")}],
         |          "resources": {
         |            "requests": {
         |              "cpu": ${ds.cpu},
         |              "memory": ${ds.mem}
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
        httpClient.post[Any](url, request, apiHeaders)
      })
  }
}
