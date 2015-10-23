package io.vamp.container_driver.docker.wrapper

import com.ning.http.client.Response
import com.typesafe.scalalogging.Logger
import dispatch.as
import io.vamp.container_driver.docker.wrapper.model._
import org.json4s.JsonAST.JObject
import org.json4s._
import org.slf4j.LoggerFactory

object Create {

  case class Response(id: String, warnings: Seq[String])

}

sealed trait Representation[T] {
  def map: Response ⇒ T
}

object Representation {

  private[Representation] trait Common {
    def strs(v: JValue) = for {
      JArray(xs) ← v
      JString(str) ← xs
    } yield str

    def optStr(name: String, fields: List[JField]) = (for {
      (`name`, JString(value)) ← fields
    } yield value).headOption

    def optLong(name: String, fields: List[JField]) = (for {
      (`name`, JInt(value)) ← fields
    } yield value.toLong).headOption
  }

  implicit val formats = DefaultFormats

  implicit val CreateResponse: Representation[Create.Response] = new Representation[Create.Response] {

    def map = { r ⇒
      (for {
        JObject(resp) ← as.json4s.Json(r)
        ("Id", JString(id)) ← resp

      } yield Create.Response(
        id = id,
        warnings = for {
          ("Warnings", JArray(warns)) ← resp
          JString(warn) ← warns
        } yield warn)
      ).head
    }
  }

  implicit val Identity: Representation[Response] = new Representation[Response] {
    def map = identity(_)
  }

  implicit val Nothing: Representation[Unit] = new Representation[Unit] {
    def map = _ ⇒ ()
  }

  implicit val Infos: Representation[Info] = new Representation[Info] {

    def map = { r ⇒
      (for {
        JObject(info) ← as.json4s.Json(r)
        ("Containers", JInt(cont)) ← info
        ("Images", JInt(images)) ← info
        ("DockerRootDir", JString(dockerRootDir)) ← info
        ("ID", JString(id)) ← info
        ("IndexServerAddress", JString(indexServerAddress)) ← info
        ("InitPath", JString(initPath)) ← info
        ("KernelVersion", JString(kernelVersion)) ← info
        ("Name", JString(name)) ← info
        ("OperatingSystem", JString(operatingSystem)) ← info
        ("SystemTime", JString(systemTime)) ← info
      } yield Info(
        containers = cont.toInt,
        images = images.toInt,
        dockerRootDir = dockerRootDir,
        id = id,
        indexServerAddress = indexServerAddress,
        initPath = initPath,
        kernelVersion = kernelVersion,
        name = name,
        operatingSystem = operatingSystem,
        systemTime = systemTime
      )).head
    }

  }

  implicit val ListOfContainers: Representation[List[Container]] = new Representation[List[Container]] with Common {

    def map = { r ⇒
      for {
        JObject(cont) ← as.json4s.Json(r)
        ("Id", JString(id)) ← cont
        ("Image", JString(image)) ← cont
        ("Command", JString(command)) ← cont
        ("Status", JString(status)) ← cont
        ("Names", JArray(names)) ← cont
        ("Ports", JArray(ps)) ← cont
      } yield Container(
        id = id,
        image = image,
        command = command,
        status = status,
        names = for { JString(name) ← names } yield name,
        ports = for {
          JObject(port) ← ps
          ("IP", JString(ip)) ← port
          ("PrivatePort", JInt(privatePort)) ← port
          ("PublicPort", JInt(publicPort)) ← port
          ("Type", JString(portType)) ← port
        } yield PortDescription(
          ip = ip,
          privatePort = privatePort.toInt,
          publicPort = publicPort.toInt,
          portType = portType)
      )
    }

  }

  implicit val ListOfImages: Representation[List[Image]] = new Representation[List[Image]] with Common {

    def map = { r ⇒
      for {
        JObject(img) ← as.json4s.Json(r)
        ("Id", JString(id)) ← img
        ("RepoTags", JArray(tags)) ← img
      } yield model.Image(id = id, repoTags = for { JString(tag) ← tags } yield tag)
    }

  }

  implicit object ContainerDetail extends Representation[ContainerDetails] with Common {
    private[this] val KeyVal = """(.+)=(.+)""".r

    private val logger = Logger(LoggerFactory.getLogger(classOf[Representation[ContainerDetails]]))

    def map = { r ⇒
      (for {
        JObject(cont) ← as.json4s.Json(r)
        ("Id", JString(id)) ← cont
        ("Name", JString(name)) ← cont
        ("Image", JString(img)) ← cont
        ("Config", JObject(config)) ← cont
        ("HostConfig", JObject(hostConfig)) ← cont
        ("NetworkSettings", JObject(networkSettings)) ← cont
      } yield ContainerDetails(
        id = id,
        name = name,
        state = containerState(cont),
        image = img,
        config = containerConfig(config),
        hostConfig = containerHostConfig(hostConfig),
        networkSettings = containerNetworkSettings(networkSettings)
      )
      ).headOption.getOrElse(failedToParseContainerDetails(r))
    }

    def failedToParseContainerDetails(r: Response): ContainerDetails = {
      logger.error(s"Failed to parse container details: ${r.getResponseBody}")
      new ContainerDetails(
        id = "Error",
        name = "Error",
        state = new ContainerState(),
        image = "Error",
        config = new ContainerConfig(image = "Error"),
        networkSettings = new NetworkSettings(bridge = "ERROR", gateway = "ERROR", ipAddress = "ERROR", ports = Map.empty),
        hostConfig = new HostConfig()
      )
    }

    private def containerHostConfig(config: List[JField]) =
      (for {
        ("Binds", binds) ← config
        ("Memory", JInt(memory)) ← config
        ("MemorySwap", JInt(memorySwap)) ← config
        ("CpuShares", JInt(cpuShares)) ← config
        ("PortBindings", JObject(portBindings)) ← config
        ("Links", links) ← config
        ("PublishAllPorts", JBool(publishAllPorts)) ← config
        ("Dns", dns) ← config
        ("DnsSearch", dnsSearch) ← config
        ("VolumesFrom", volumesFrom) ← config
        ("NetworkMode", JString(NetworkMode(netMode))) ← config
      } yield HostConfig(
        binds = strs(binds).map(VolumeBinding.parse(_)),
        memory = memory.toInt,
        memorySwap = memorySwap.toInt,
        cpuShares = cpuShares.toInt,

        portBindings = (
          for { (Port(port), bindings) ← portBindings } yield (port, portBinding(bindings))
        ).toMap,

        links = strs(links),
        publishAllPorts = publishAllPorts,
        dns = strs(dns),
        dnsSearch = strs(dnsSearch),
        volumesFrom = strs(volumesFrom).map(VolumeFromBinding.parse(_)),
        networkMode = (for {
          ("NetworkMode", JString(NetworkMode(netMode))) ← config
        } yield netMode).headOption.getOrElse(NetworkMode.Bridge)
      )
      ).headOption.getOrElse(failedToParseHostConfig)

    def failedToParseHostConfig: HostConfig = {
      logger.error(s"Failed to parse host Config")
      new HostConfig()
    }

    def portBinding(bindings: JValue): List[PortBinding] = {
      for {
        JArray(xs) ← bindings
        JObject(binding) ← xs
        ("HostIp", JString(hostIp)) ← binding
        ("HostPort", JString(hostPort)) ← binding
      } yield PortBinding(hostIp = hostIp, hostPort = hostPort.toInt)
    }

    private def containerNetworkSettings(settings: List[JField]) =
      (for {
        ("Bridge", JString(bridge)) ← settings
        ("Gateway", JString(gateway)) ← settings
        ("IPAddress", JString(ip)) ← settings
      } yield NetworkSettings(
        bridge = bridge,
        gateway = gateway,
        ipAddress = ip,
        ports = (for {
          ("Ports", JObject(ports)) ← settings
          (Port(port), mappings) ← ports
        } yield {
          (port, portBinding(mappings))
        }).toMap
      )
      ).headOption.getOrElse(failedToParseNetworkSettings(settings))

    def failedToParseNetworkSettings(settings: List[JField]): NetworkSettings = {
      logger.error(s"Failed to parse container network settings: $settings")
      new NetworkSettings(bridge = "ERROR", gateway = "ERROR", ipAddress = "ERROR", ports = Map.empty)
    }

    private def containerState(cont: List[JField]) =
      (for {
        ("State", JObject(state)) ← cont
        ("Running", JBool(running)) ← state
        ("Paused", JBool(paused)) ← state
        ("Restarting", JBool(restarting)) ← state
        ("OOMKilled", JBool(oomKilled)) ← state
        ("Dead", JBool(dead)) ← state
        ("Pid", JInt(pid)) ← state
        ("ExitCode", JInt(exitCode)) ← state
        ("Error", JString(error)) ← state
        ("StartedAt", JString(startedAt)) ← state
        ("FinishedAt", JString(finishedAt)) ← state
      } yield ContainerState(
        running = running,
        paused = paused,
        restarting = restarting,
        oomKilled = oomKilled,
        dead = dead,
        pid = pid.toInt,
        exitCode = exitCode.toInt,
        error = error,
        startedAt = startedAt,
        finishedAt = finishedAt)
      ).head

    def containerConfig(cfg: List[JField]) =
      (for {
        ("Hostname", JString(hostName)) ← cfg
        ("Domainname", JString(domainName)) ← cfg
        ("Env", JArray(env)) ← cfg
        ("Image", JString(image)) ← cfg
        ("Volumes", volumes) ← cfg
        ("WorkingDir", JString(workingDir)) ← cfg
      } yield ContainerConfig(
        hostName = hostName,
        domainName = domainName,
        exposedPorts = for {
          ("ExposedPorts", JArray(ep)) ← cfg
          JString(p) ← ep
        } yield p,
        env = (for { JString(KeyVal(k, v)) ← env } yield (k, v)).toMap,
        image = image,
        volumes = strs(volumes),
        workingDir = workingDir
      )).headOption.getOrElse(failedToParseContainerConfig(cfg))

    def failedToParseContainerConfig(cfg: List[JField]): ContainerConfig = {
      logger.error(s"Failed to parse container config: $cfg")
      new ContainerConfig(image = "ERROR")
    }

  }

}
