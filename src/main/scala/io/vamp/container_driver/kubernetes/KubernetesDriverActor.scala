package io.vamp.container_driver.kubernetes

import com.typesafe.config.ConfigFactory
import io.vamp.common.http.RestClient
import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver._
import io.vamp.container_driver.notification.UnsupportedContainerDriverRequest

import scala.concurrent.Future

object KubernetesDriverActor {

  private val configuration = ConfigFactory.load().getConfig("vamp.container-driver")

  val kubernetesUrl = configuration.getString("kubernetes.url")
}

case class KubernetesDriverInfo(version: Any, paths: Any, api: Any, apis: Any)

class KubernetesDriverActor extends ContainerDriverActor with ContainerDriver with KubernetesDaemonSet {

  val kubernetesUrl = KubernetesDriverActor.kubernetesUrl

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
}
