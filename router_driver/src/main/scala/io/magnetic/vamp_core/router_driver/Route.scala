package io.magnetic.vamp_core.router_driver

case class Filter(name: String, condition: String, destination: String)

case class HttpQuota(sampleWindow: String, rate: Int, expiryTime: String)

case class TcpQuota(sampleWindow: String, rate: Int, expiryTime: String)

case class Server(name: String, host: String, port: Int)

case class Group(name: String, weight: Int, servers: List[Server])

case class Route(name: String, port: Int, protocol: String, filters: List[Filter], httpQuota: Option[HttpQuota], tcpQuota: Option[TcpQuota], groups: List[Group])

case class ClusterRoute(deploymentName: String, clusterName: String, portNumber: Int, groups: List[Group])