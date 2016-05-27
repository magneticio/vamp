package io.vamp.container_driver.kubernetes

import akka.actor.ActorLogging
import io.vamp.common.crypto.Hash
import io.vamp.common.http.RestClient

import scala.concurrent.Future

case class KubernetesServicePort(name: String, protocol: String, port: Int, targetPort: Int)

object KubernetesServiceType extends Enumeration {
  val NodePort, LoadBalancer = Value
}

trait KubernetesService extends KubernetesArtifact {
  this: KubernetesContainerDriver with ActorLogging ⇒

  private lazy val url = s"$kubernetesUrl/api/v1/namespaces/default/services"

  private val nameMatcher = """^[a-z]([-a-z0-9]*[a-z0-9])?$""".r

  protected def services(labels: Map[String, String] = Map()): Future[KubernetesApiResponse] = {
    def request(u: String) = RestClient.get[KubernetesApiResponse](u)
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
         |      "app": "$selector"
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
          RestClient.put[Any](s"$url/$id", request)
        } else {
          log.debug(s"Service exists: $name")
          Future.successful(false)
        }
      },
      () ⇒ {
        log.info(s"Creating service: $name")
        RestClient.post[Any](url, request)
      }
    )
  }

  protected def deleteServiceById(id: String): Future[Any] = {
    retrieve(url, id,
      () ⇒ {
        log.info(s"Deleting service: $id")
        RestClient.delete(s"$url/$id")
      },
      () ⇒ {
        log.debug(s"Service does not exist: $id")
        Future.successful(false)
      }
    )
  }

  private def toId(name: String): String = name match {
    case nameMatcher(_*) if name.length < 25 ⇒ name
    case _                                   ⇒ s"hex${Hash.hexSha1(name).substring(0, 20)}"
  }
}
