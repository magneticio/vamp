package io.vamp.container_driver.rancher

case object AllApps

case class DeployApp(service: Service, update: Boolean)

case class RancherResponse(id: String, status: Int, links: Option[List[String]])

case class Stack(state: Option[String], id: Option[String], name: String, actions: Option[Map[String, String]])

case class LaunchConfig(
  imageUuid: String,
  labels: Option[Map[String, String]],
  privileged: Option[Boolean],
  startOnCreate: Boolean = false,
  cpuShares: Option[Int] = None,
  memoryMb: Option[Int] = None,
  networkMode: String = "managed",
  ports: List[String] = Nil,
  environment: Map[String, String] = Map())

case class ServiceContainersList(data: List[RancherContainer])

case class RancherContainer(id: String, name: String, primaryIpAddress: String, ports: List[String], created: String, state: String)

case class RancherContainerPortList(data: List[RancherContainerPort])

case class RancherContainerPort(privatePort: Option[String], protocol: Option[String])

case class ServiceList(data: List[Service])

case class UpdateService(scale: Int)

case class Service(state: Option[String],
                   environmentId: String,
                   id: Option[String],
                   name: String,
                   scale: Option[Int],
                   launchConfig: Option[LaunchConfig],
                   actions: Option[Map[String, String]],
                   containers: Option[List[RancherContainer]],
                   startOnCreate: Boolean)

case class ProjectInfo(id: String, state: String)

case class Stacks(data: List[Stack])