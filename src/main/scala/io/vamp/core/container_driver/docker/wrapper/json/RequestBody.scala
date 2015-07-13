package io.vamp.core.container_driver.docker.wrapper.json

import io.vamp.core.container_driver.docker.wrapper.model._
import org.json4s.JsonAST.JObject
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods.{compact, render}

class RequestBody {

  // https://docs.docker.com/reference/api/docker_remote_api_v1.19/#create-a-container

  def requestCreate(config: ContainerConfig, name: Option[String] = None) =
    compact(render(
      ("Hostname" -> config.hostName) ~
        ("Domainname" -> config.domainName) ~
        ("Env" -> config.env.map { case (k, v) => s"$k=$v" }) ~
        ("ExposedPorts" -> config.exposedPorts.map(ep => ep -> JObject()).toMap) ~
        ("Image" -> config.image) ~
        ("Volumes" -> config.volumes.map(vol => (vol, JObject())).toMap) ~
        ("WorkingDir" -> config.workingDir)
    ))


  // https://docs.docker.com/reference/api/docker_remote_api_v1.19/#start-a-container

  // TODO Add memory & cpu settings
  def requestStart(hostConfig: HostConfig) = compact(render(
    ("Binds" -> Option(hostConfig.binds.map(_.spec)).filter(_.nonEmpty)) ~
      ("Links" -> Option(hostConfig.links).filter(_.nonEmpty)) ~
      //     ("Memory" -> hostConfig.memory) ~
      //("MemorySwap" -> hostConfig.memorySwap) ~
      //      ("CpuShares" -> hostConfig.cpuShares) ~
      ("PortBindings" -> hostConfig.portBindings.map {
        case (port, bindings) => port.spec -> bindings.map { binding => ("HostIp" -> binding.hostIp) ~ ("HostPort" -> binding.hostPort.toString) }
      }) ~
      ("PublishAllPorts" -> hostConfig.publishAllPorts) ~
      ("Dns" -> Option(hostConfig.dns).filter(_.nonEmpty)) ~
      ("DnsSearch" -> Option(hostConfig.dnsSearch).filter(_.nonEmpty)) ~
      ("VolumesFrom" -> Option(hostConfig.volumesFrom.map(_.spec)).filter(_.nonEmpty)) ~
      ("NetworkMode" -> hostConfig.networkMode.value)
  ))


}
