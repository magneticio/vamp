package io.vamp.container_driver.rancher.api

case class RancherResponse(id: String, status: Int, links: Option[List[String]])

case class Stack(id: Option[String], name: String, actions: Option[Map[String, String]])

case class LaunchConfig(imageUuid: String, labels: Option[Map[String, String]], startOnCreate: Boolean = true, cpuSet: Option[String] = None, memoryMb: Option[String] = None)

case class ServiceContainersList(data: List[RancherContainer])

case class RancherContainer(id: String, name: String, primaryIpAddress: String, ports: List[RancherContainerPort], created: String)

case class RancherContainerPortList(data: List[RancherContainerPort])

case class RancherContainerPort(privatePort: String, protocol: String)

case class ServiceList(data: List[Service])

case class Service(state: Option[String], environmentId: String, id: Option[String], name: String, scale: Int, launchConfig: Option[LaunchConfig], actions: Option[Map[String, String]], containers: Option[List[RancherContainer]] = None, startOnCreate: Boolean = true)

case class ProjectInfo(id: String, state: String)