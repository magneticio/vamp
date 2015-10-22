package io.vamp.core.router_driver.haproxy

case class HaProxyModel(frontends: List[Frontend], backends: List[Backend])

case class Frontend(name: String,
                    bindIp: Option[String],
                    bindPort: Option[Int],
                    mode: HaProxyInterface.Mode.Value,
                    unixSock: Option[String],
                    sockProtocol: Option[String],
                    options: HaProxyOptions,
                    filters: List[HaProxyFilter],
                    defaultBackend: String)

case class Backend(name: String,
                   mode: HaProxyInterface.Mode.Value,
                   proxyServers: List[HaProxyProxyServer],
                   servers: List[HaProxyServer],
                   options: HaProxyOptions)