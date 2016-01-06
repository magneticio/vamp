package io.vamp.gateway_driver.haproxy

case class HaProxy(frontends: List[Frontend], backends: List[Backend], tcpLogFormat: String, httpLogFormat: String)

case class Frontend(name: String,
                    lookup: String,
                    bindIp: Option[String],
                    bindPort: Option[Int],
                    mode: Mode.Value,
                    unixSock: Option[String],
                    sockProtocol: Option[String],
                    options: Options,
                    filters: List[Filter],
                    defaultBackend: Backend)

case class Backend(name: String,
                   lookup: String,
                   mode: Mode.Value,
                   proxyServers: List[ProxyServer],
                   servers: List[Server],
                   sticky: Boolean,
                   options: Options)

object Mode extends Enumeration {
  val http, tcp = Value
}

case class Filter(name: String, condition: String, destination: Backend, negate: Boolean = false)

case class ProxyServer(name: String, lookup: String, unixSock: String, weight: Int)

case class Server(name: String, lookup: String, host: String, port: Int, weight: Int, maxConn: Int = 1000, checkInterval: Option[Int] = None)

case class Options(abortOnClose: Boolean = false,
                   allBackups: Boolean = false,
                   checkCache: Boolean = false,
                   forwardFor: Boolean = false,
                   httpClose: Boolean = false,
                   httpCheck: Boolean = false,
                   sslHelloCheck: Boolean = false,
                   tcpKeepAlive: Boolean = false,
                   tcpSmartAccept: Boolean = false,
                   tcpSmartConnect: Boolean = false,
                   tcpLog: Boolean = false)
