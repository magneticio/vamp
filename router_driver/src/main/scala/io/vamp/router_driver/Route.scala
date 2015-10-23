package io.vamp.router_driver

case class Filter(name: Option[String], condition: String, destination: String)

case class Server(name: String, host: String, port: Int)

case class Service(name: String, weight: Int, servers: List[Server])

case class Route(name: String, port: Int, protocol: String, filters: List[Filter], services: List[Service])
