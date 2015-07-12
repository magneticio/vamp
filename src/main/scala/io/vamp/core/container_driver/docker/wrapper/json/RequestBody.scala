package io.vamp.core.container_driver.docker.wrapper.json

import io.vamp.core.container_driver.docker.wrapper.model._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods.{compact, render}

class RequestBody {

  // https://docs.docker.com/reference/api/docker_remote_api_v1.19/#create-a-container

  //TODO add missing fields
  def requestCreate(config: ContainerConfig, name: Option[String] = None) =
    compact(render(
      //("Hostname" -> config.hostName) ~
      //("Domainname" -> config.domainName) ~
      ("Env" -> config.env.map { case (k, v) => s"$k=$v" }) ~
        //("Cmd" -> Option(config.cmd).filter(_.nonEmpty)) ~
        //"Entrypoint"
        ("Image" -> config.image)
      //"Labels"
      //("Volumes" -> config.volumes.map { vol => (vol, JObject()) }) ~
      //("WorkingDir" -> config.workingDir) ~
      //("ExposedPorts" -> config.exposedPorts.map { ep => (ep -> JObject())})
      // HostConfig
    ))

  /*
  {
         "HostConfig": {
           "Binds": ["/tmp:/tmp"],
           "Links": ["redis3:redis"],
           "LxcConf": {"lxc.utsname":"docker"},
           "Memory": 0,
           "MemorySwap": 0,
           "CpuShares": 512,
           "CpuPeriod": 100000,
           "CpusetCpus": "0,1",
           "CpusetMems": "0,1",
           "BlkioWeight": 300,
           "OomKillDisable": false,
           "PortBindings": { "22/tcp": [{ "HostPort": "11022" }] },
           "PublishAllPorts": false,
           "Privileged": false,
           "ReadonlyRootfs": false,
           "Dns": ["8.8.8.8"],
           "DnsSearch": [""],
           "ExtraHosts": null,
           "VolumesFrom": ["parent", "other:ro"],
           "CapAdd": ["NET_ADMIN"],
           "CapDrop": ["MKNOD"],
           "RestartPolicy": { "Name": "", "MaximumRetryCount": 0 },
           "NetworkMode": "bridge",
           "Devices": [],
           "Ulimits": [{}],
           "LogConfig": { "Type": "json-file", "Config": {} },
           "SecurityOpt": [""],
           "CgroupParent": ""
        }
    }

     */


  // https://docs.docker.com/reference/api/docker_remote_api_v1.19/#start-a-container

  // TODO add missing fields
  def requestStart(hostConfig: HostConfig) = compact(render(
    //("Binds" -> Option(hostConfig.binds.map(_.spec)).filter(_.nonEmpty)) ~
    ("Links" -> Option(hostConfig.links).filter(_.nonEmpty)) ~
      ("Memory" -> hostConfig.memory) ~
      ("MemorySwap" -> hostConfig.memorySwap) ~
      ("CpuShares" -> hostConfig.cpuShares) ~
      ("CpusetCpus" -> hostConfig.cpusetCpu) ~
      //      ("PortBindings" -> hostConfig.portBindings.map {
      //        case (port, bindings) => (port.spec -> bindings.map { binding => ("HostIp" -> binding.hostIp) ~ ("HostPort" -> binding.hostPort.toString) })
      //      }) ~
      ("PublishAllPorts" -> hostConfig.publishAllPorts) ~
      ("Dns" -> Option(hostConfig.dns).filter(_.nonEmpty)) ~
      ("DnsSearch" -> Option(hostConfig.dnsSearch).filter(_.nonEmpty)) ~
      ("VolumesFrom" -> Option(hostConfig.volumesFrom.map(_.spec)).filter(_.nonEmpty)) ~
      ("NetworkMode" -> hostConfig.networkMode.value)
    // "Devices": [],
  ))


}
