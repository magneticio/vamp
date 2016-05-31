package io.vamp.container_driver.rancher

import io.vamp.common.vitals.InfoRequest
import io.vamp.container_driver.ContainerDriverActor.{ All, Deploy, DeployedGateways, Undeploy }
import io.vamp.container_driver._
import io.vamp.container_driver.rancher.api.DeployApp
import io.vamp.container_driver.notification.UnsupportedContainerDriverRequest
import org.apache.commons.codec.Charsets
import com.typesafe.config.ConfigFactory

private[rancher] object Credentials {

  def credentials(user: String, password: String): String = {
    val encoder = new sun.misc.BASE64Encoder
    val base64Auth = s"$user:$password"
    encoder.encode(base64Auth.getBytes(Charsets.UTF_8)).replace("\n", "")
  }
}

object RancherDriverActor {
  private val configuration = ConfigFactory.load().getConfig("vamp.container-driver")

  val rancherUrl = configuration.getString("rancher.url")
}

class RancherDriverActor extends RancherDriver with ContainerDriverActor {

  private val configuration = ConfigFactory.load().getConfig("vamp.container-driver")

  val rancherUrl = configuration.getString("rancher.url")

  import io.vamp.container_driver.rancher.api.AllApps

  def receive = {
    case InfoRequest                ⇒ reply(info)
    case All                        ⇒ reply(all)
    case AllApps                    ⇒ reply(allApps)
    case d: Deploy                  ⇒ reply(deploy(d.deployment, d.cluster, d.service, d.update))
    case s: DeployApp               ⇒ reply(deployApp(s.service, s.update))
    case u: Undeploy                ⇒ reply(undeploy(u.deployment, u.service))
    case DeployedGateways(gateways) ⇒ reply(deployedGateways(gateways))
    case any                        ⇒ unsupported(UnsupportedContainerDriverRequest(any))
  }
}