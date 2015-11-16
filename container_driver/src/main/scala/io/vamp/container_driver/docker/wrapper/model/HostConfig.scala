package io.vamp.container_driver.docker.wrapper.model

case class HostConfig(
  binds: Seq[VolumeBinding] = Seq.empty,
  memory: Long = 4 * 1024 * 1024, //Default to min. memory requirement
  memorySwap: Long = 0,
  cpuShares: Int = 2, // defaults to 2 shares (min requirement)
  portBindings: Map[Port, List[PortBinding]] = Map.empty,
  links: Seq[String] = Seq.empty,
  publishAllPorts: Boolean = false,
  dns: Seq[String] = Seq.empty,
  dnsSearch: Seq[String] = Seq.empty,
  volumesFrom: Seq[VolumeFromBinding] = Seq.empty,
  networkMode: NetworkMode = NetworkMode.Bridge)
