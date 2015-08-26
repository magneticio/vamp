package io.vamp.core.container_driver.docker.wrapper.model

case class ContainerConfig(
  hostName: String = "",
  domainName: String = "",
  exposedPorts: Seq[String] = Seq.empty,
  env: Map[String, String] = Map.empty,
  image: String,
  volumes: Seq[String] = Seq.empty,
  workingDir: String = "")

