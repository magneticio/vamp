package io.vamp.container_driver.docker.wrapper.model

case class PortBinding(hostIp: String, hostPort: Int)

object PortBinding {

  def local(port: Int) = PortBinding("0.0.0.0", port)

}

