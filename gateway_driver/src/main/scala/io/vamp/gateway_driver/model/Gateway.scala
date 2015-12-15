package io.vamp.gateway_driver.model

import io.vamp.model.artifact.DefaultGateway

case class Filter(name: Option[String], condition: String, destination: String)

case class Instance(name: String, host: String, port: Int)

case class Service(name: String, weight: Int, instances: List[Instance])

case class Gateway(name: String, port: Int, protocol: String, filters: List[Filter], services: List[Service], sticky: Option[DefaultGateway.Sticky.Value])
