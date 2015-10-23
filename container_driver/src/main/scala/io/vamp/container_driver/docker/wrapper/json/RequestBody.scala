package io.vamp.container_driver.docker.wrapper.json

import io.vamp.container_driver.docker.wrapper.model._

class RequestBody {

  /**
   * https://docs.docker.com/reference/api/docker_remote_api_v1.19/#create-a-container
   */
  def requestCreate(config: ContainerConfig, name: Option[String] = None): Map[String, Any] = Map(
    "Hostname" -> config.hostName,
    "Domainname" -> config.domainName,
    "Env" -> config.env.map { case (k, v) ⇒ s"$k=$v" },
    "ExposedPorts" -> config.exposedPorts.map(_ -> Map()).toMap,
    "Image" -> config.image,
    "Volumes" -> config.volumes.map(_ -> Map()).toMap,
    "WorkingDir" -> config.workingDir
  )

  /**
   * https://docs.docker.com/reference/api/docker_remote_api_v1.19/#start-a-container
   */
  // TODO Add memory & cpu settings
  def requestStart(hostConfig: HostConfig): Map[String, Any] = Map(
    "Binds" -> Option(hostConfig.binds.map(_.spec)).filter(_.nonEmpty),
    "Links" -> Option(hostConfig.links).filter(_.nonEmpty),
    //      "Memory" -> hostConfig.memory,
    //      "MemorySwap" -> hostConfig.memorySwap,
    //      "CpuShares" -> hostConfig.cpuShare,
    "PortBindings" -> hostConfig.portBindings.map {
      case (port, bindings) ⇒ port.spec -> bindings.map(binding ⇒ Map("HostIp" -> binding.hostIp, "HostPort" -> binding.hostPort.toString))
    },
    "PublishAllPorts" -> hostConfig.publishAllPorts,
    "Dns" -> Option(hostConfig.dns).filter(_.nonEmpty),
    "DnsSearch" -> Option(hostConfig.dnsSearch).filter(_.nonEmpty),
    "VolumesFrom" -> Option(hostConfig.volumesFrom.map(_.spec)).filter(_.nonEmpty),
    "NetworkMode" -> hostConfig.networkMode.value
  )
}
