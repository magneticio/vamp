package io.vamp.core.container_driver.docker.wrapper.model

case class PortDescription(ip: String, privatePort: Int, publicPort: Int, portType: String)

