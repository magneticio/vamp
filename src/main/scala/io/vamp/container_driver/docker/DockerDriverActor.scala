package io.vamp.container_driver.docker

import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.ContainerDriverActor.{ All, Deploy, Undeploy }
import io.vamp.container_driver._
import io.vamp.container_driver.notification.UnsupportedContainerDriverRequest

class DockerDriverActor extends DockerDriver with ContainerDriverActor {

  def receive = {
    case InfoRequest ⇒ reply(info)
    case All         ⇒ reply(all)
    case d: Deploy   ⇒ reply(deploy(d.deployment, d.cluster, d.service, d.update))
    case u: Undeploy ⇒ reply(undeploy(u.deployment, u.service))
    case any         ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }
}
