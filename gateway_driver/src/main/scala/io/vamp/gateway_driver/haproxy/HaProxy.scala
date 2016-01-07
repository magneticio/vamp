package io.vamp.gateway_driver.haproxy

import io.vamp.common.crypto.Hash

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

case class Filter(name: String, destination: Backend, acls: List[Acl], rewrites: List[Rewrite])

case class Acl(definition: String, negate: Boolean = false) {
  val name = Hash.hexSha1(definition).substring(0, 16)
}

case class Rewrite(path: String, condition: String)

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
