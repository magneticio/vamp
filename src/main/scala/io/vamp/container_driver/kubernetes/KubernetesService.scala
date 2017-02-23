package io.vamp.container_driver.kubernetes

import akka.actor.ActorLogging
import io.vamp.common.util.HashUtil
import io.vamp.container_driver.ContainerDriver

import scala.concurrent.Future

case class KubernetesServicePort(name: String, protocol: String, port: Int, targetPort: Int)

object KubernetesServiceType extends Enumeration {
  val NodePort, LoadBalancer = Value
}

trait KubernetesService extends KubernetesArtifact {
  this: KubernetesContainerDriver with ActorLogging ⇒

  private lazy val url = s"$apiUrl/api/v1/namespaces/$namespace/services"

  private val nameMatcher = """^[a-z]([-a-z0-9]*[a-z0-9])?$""".r

  protected def services(labels: Map[String, String] = Map()): Future[KubernetesApiResponse] = {
    def request(u: String) = httpClient.get[KubernetesApiResponse](u, apiHeaders)
    if (labels.isEmpty) request(url) else request(s"$url?${labelSelector(labels)}")
  }

  protected def createService(name: String, `type`: KubernetesServiceType.Value, selector: String, ports: List[KubernetesServicePort], update: Boolean, labels: Map[String, String] = Map()): Future[Any] = {
    val id = toId(name)
    val request =
      s"""
         |{
         |  "kind": "Service",
         |  "apiVersion": "v1",
         |  "metadata": {
         |    "name": "$id",
         |    ${labels2json(labels)}
         |  },
         |  "spec": {
         |    "selector": {
         |      "${ContainerDriver.namespace()}": "$selector"
         |    },
         |    "ports": [${ports.map(p ⇒ s"""{"name": "p${p.name}", "protocol": "${p.protocol.toUpperCase}", "port": ${p.port}, "targetPort": ${p.targetPort}}""").mkString(", ")}],
         |    "type": "${`type`.toString}"
         |  }
         |}
   """.stripMargin
    retrieve(url, id,
      () ⇒ {
        if (update) {
          log.info(s"Updating service: $name")
          httpClient.put[Any](s"$url/$id", request, apiHeaders)
        } else {
          log.debug(s"Service exists: $name")
          Future.successful(false)
        }
      },
      () ⇒ {
        log.info(s"Creating service: $name")
        httpClient.post[Any](url, request, apiHeaders)
      })
  }

  protected def deleteServiceById(id: String): Future[Any] = {
    retrieve(url, id,
      () ⇒ {
        log.info(s"Deleting service: $id")
        httpClient.delete(s"$url/$id", apiHeaders)
      },
      () ⇒ {
        log.debug(s"Service does not exist: $id")
        Future.successful(false)
      })
  }

  private def toId(name: String): String = name match {
    case nameMatcher(_*) if name.length < 25 ⇒ name
    case _                                   ⇒ s"hex${HashUtil.hexSha1(name).substring(0, 20)}"
  }
}
