package io.vamp.container_driver.kubernetes

import akka.actor.ActorLogging
import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.common.http.RestClient

import scala.concurrent.Future
import scala.util.Try

trait KubernetesDaemonSet {
  this: ActorLogging with ExecutionContextProvider ⇒

  def kubernetesUrl: String

  protected def daemonSet(ds: DaemonSet): Unit = for {
    _ ← daemonSetCreate(ds)
    _ ← daemonSetServiceCreate(ds)
  } yield {}

  private def daemonSetCreate(ds: DaemonSet): Future[Any] = {
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

    process(url, ds.name,
      () ⇒ {
        log.debug(s"Daemon set exists: ${ds.name}")
      },
      () ⇒ {
        log.info(s"Creating daemon set: ${ds.name}")
        RestClient.post[Any](url, request)
      }
    )
  }

  private def daemonSetServiceCreate(ds: DaemonSet): Future[Any] = {
    val url = s"$kubernetesUrl/api/v1/namespaces/default/services"
    val request =
      s"""
         |{
         |  "kind": "Service",
         |  "apiVersion": "v1",
         |  "metadata": {
         |    "name": "${ds.name}"
         |  },
         |  "spec": {
         |    "selector": {
         |      "app": "${ds.name}"
         |    },
         |    "ports": [${ds.docker.portMappings.map(pm ⇒ s"""{"name": "p${pm.containerPort}", "protocol": "${pm.protocol.toUpperCase}", "port": ${pm.hostPort}, "targetPort": ${pm.containerPort}}""").mkString(", ")}],
         |    "type": "NodePort"
         |  }
         |}
   """.stripMargin

    process(url, ds.name,
      () ⇒ {
        log.debug(s"Service exists: ${ds.name}")
      },
      () ⇒ {
        log.info(s"Creating service: ${ds.name}")
        RestClient.post[Any](url, request)
      }
    )
  }

  private def process(url: String, name: String, exists: () ⇒ Unit, notExists: () ⇒ Future[Any]): Future[Any] = RestClient.get[Any](url).map {
    case response: Map[_, _] ⇒ Try(
      response.asInstanceOf[Map[String, _]].get("items") match {
        case Some(list: List[_]) ⇒ list.map(_.asInstanceOf[Map[String, _]].get("metadata").get.asInstanceOf[Map[String, _]].get("name").get)
        case _                   ⇒ Nil
      }
    ).getOrElse(Nil).contains(name)
    case _ ⇒ false
  } flatMap {
    case true ⇒
      exists()
      Future.successful(false)
    case false ⇒ notExists()
  }
}
