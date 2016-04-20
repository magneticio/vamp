package io.vamp.container_driver.rancher

import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.ContainerDriverActor.{ All, Deploy, Undeploy }
import io.vamp.container_driver._
import io.vamp.container_driver.notification.UnsupportedContainerDriverRequest
import org.apache.commons.codec.Charsets

private[rancher] object Credentials {

  def credentials(user: String, password: String): String = {
    val encoder = new sun.misc.BASE64Encoder
    val base64Auth = s"${user}:${password}"
    encoder.encode(base64Auth.getBytes(Charsets.UTF_8)).replace("\n", "")
  }
}

class RancherDriverActor extends RancherDriver with ContainerDriverActor {

  def receive = {
    case InfoRequest ⇒ reply(info)
    case All         ⇒ reply(all)
    case d: Deploy   ⇒ reply(deploy(d.deployment, d.cluster, d.service, d.update))
    case u: Undeploy ⇒ reply(undeploy(u.deployment, u.service))
    case any         ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }
}