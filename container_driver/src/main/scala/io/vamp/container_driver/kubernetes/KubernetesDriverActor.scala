package io.vamp.container_driver.kubernetes

import com.typesafe.config.ConfigFactory
import io.vamp.common.http.RestClient
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver._
import io.vamp.container_driver.notification.UnsupportedContainerDriverRequest

import scala.concurrent.Future
import scala.util.Try

object KubernetesDriverActor {

  private val configuration = ConfigFactory.load().getConfig("vamp.container-driver")

  val kubernetesUrl = configuration.getString("kubernetes.url")
}

case class KubernetesDriverInfo(version: Any, paths: Any, api: Any, apis: Any)

class KubernetesDriverActor extends ContainerDriverActor with ContainerDriver {

  import KubernetesDriverActor._

  def receive = {
    case InfoRequest   ⇒ reply(info)
    case ds: DaemonSet ⇒ daemonSet(ds)
    case any           ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }

  private def info: Future[Any] = for {
    paths ← RestClient.get[Any](s"$kubernetesUrl") map {
      case map: Map[_, _] ⇒ map.headOption.map { case (_, value) ⇒ value }
      case any            ⇒ any
    }
    api ← RestClient.get[Any](s"$kubernetesUrl/api")
    apis ← RestClient.get[Any](s"$kubernetesUrl/apis")
    version ← RestClient.get[Any](s"$kubernetesUrl/version")
  } yield {
    ContainerInfo("kubernetes", KubernetesDriverInfo(version, paths, api, apis))
  }

  private def daemonSet(ds: DaemonSet) = {

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
      """.stripMargin.stripLineEnd

    def default = {
      log.info(s"Creating daemon set: ${ds.name}")
      RestClient.post[Any](url, request)
    }

    RestClient.get[Any](url).flatMap {
      case response: Map[_, _] ⇒

        val daemons = Try(
          response.asInstanceOf[Map[String, _]].get("items") match {
            case Some(list: List[_]) ⇒ list.map(_.asInstanceOf[Map[String, _]].get("metadata").get.asInstanceOf[Map[String, _]].get("name").get)
            case _                   ⇒ Nil
          }
        ).getOrElse(Nil)

        if (daemons.contains(ds.name)) {
          log.debug(s"Daemon set exists: ${ds.name}")
          Future.successful(true)
        } else default

      case _ ⇒ default
    }
  }
}
