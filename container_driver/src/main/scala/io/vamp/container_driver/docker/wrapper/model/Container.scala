package io.vamp.container_driver.docker.wrapper.model

case class Container(
  id: String,
  image: String,
  command: String,
  status: String,
  ports: Seq[PortDescription],
  names: Seq[String])

