package io.vamp.container_driver.kubernetes

import akka.actor.ActorLogging
import io.vamp.common.crypto.Hash
import io.vamp.common.http.RestClient

import scala.concurrent.Future

case class KubernetesServicePort(name: String, protocol: String, port: Int, targetPort: Int)

trait KubernetesService {
  this: KubernetesContainerDriver with ActorLogging ⇒

  private lazy val url = s"$kubernetesUrl/api/v1/namespaces/default/services"

  private val nameMatcher = """^[a-z]([-a-z0-9]*[a-z0-9])?$""".r

  protected def createService(name: String, selector: String, ports: List[KubernetesServicePort], update: Boolean): Future[Any] = {
    val id = toId(name)
    val request =
      s"""
         |{
         |  "kind": "Service",
         |  "apiVersion": "v1",
         |  "metadata": {
         |    "name": "$id"
         |  },
         |  "spec": {
         |    "selector": {
         |      "app": "$selector"
         |    },
         |    "ports": [${ports.map(p ⇒ s"""{"name": "p${p.name}", "protocol": "${p.protocol.toUpperCase}", "port": ${p.port}, "targetPort": ${p.targetPort}}""").mkString(", ")}],
         |    "type": "NodePort"
         |  }
         |}
   """.stripMargin

    retrieve(url, name,
      () ⇒ {
        if (update) {
          log.info(s"Updating service: $name")
          RestClient.put[Any](s"$url/$name", request)
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

  protected def deleteService(name: String): Future[Any] = {
    val id = toId(name)
    retrieve(url, id,
      () ⇒ {
        log.info(s"Deleting service: $name")
        RestClient.delete(s"$url/$id")
      },
      () ⇒ {
        log.debug(s"Service does not exist: $name")
        Future.successful(false)
      }
    )
  }

  private def toId(name: String): String = name match {
    case nameMatcher(_*) if name.length < 25 ⇒ name
    case _                                   ⇒ s"hex${Hash.hexSha1(name).substring(0, 20)}"
  }
}
