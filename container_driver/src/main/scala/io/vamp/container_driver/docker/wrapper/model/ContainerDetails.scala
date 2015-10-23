package io.vamp.container_driver.docker.wrapper.model

case class ContainerDetails(
  id: String,
  name: String,
  config: ContainerConfig,
  state: ContainerState,
  image: String,
  networkSettings: NetworkSettings,
  hostConfig: HostConfig)
