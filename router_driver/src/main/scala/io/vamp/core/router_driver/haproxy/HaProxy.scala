package io.vamp.core.router_driver.haproxy

case class HaProxy(frontends: List[Frontend], backends: List[Backend])

case class Frontend(name: String,
                    bindIp: Option[String],
                    bindPort: Option[Int],
                    mode: Interface.Mode.Value,
                    unixSock: Option[String],
                    sockProtocol: Option[String],
                    options: Options,
                    filters: List[Filter],
                    defaultBackend: String)

case class Backend(name: String,
                   mode: Interface.Mode.Value,
                   proxyServers: List[ProxyServer],
                   servers: List[Server],
                   options: Options)

object Interface {

  object Mode extends Enumeration {
    val http, tcp = Value
  }

}

case class Filter(name: String, condition: String, destination: String, negate: Boolean = false)

case class ProxyServer(name: String, unixSock: String, weight: Int)

case class Server(name: String, host: String, port: Int, weight: Int, maxConn: Int = 1000, checkInterval: Option[Int] = None)

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
