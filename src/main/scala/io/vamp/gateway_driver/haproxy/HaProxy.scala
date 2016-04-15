package io.vamp.gateway_driver.haproxy

import io.vamp.common.crypto.Hash

case class HaProxy(version: String,
                   frontends: List[Frontend],
                   backends: List[Backend],
                   virtualHostFrontends: List[Frontend],
                   virtualHostBackends: List[Backend],
                   tcpLogFormat: String,
                   httpLogFormat: String)

case class Frontend(name: String,
                    lookup: String,
                    bindIp: Option[String],
                    bindPort: Option[Int],
                    mode: Mode.Value,
                    unixSock: Option[String],
                    sockProtocol: Option[String],
                    filters: List[Filter],
                    defaultBackend: Backend,
                    options: Options)

case class Backend(name: String,
                   lookup: String,
                   mode: Mode.Value,
                   proxyServers: List[ProxyServer],
                   servers: List[Server],
                   rewrites: List[Rewrite],
                   sticky: Boolean,
                   balance: String,
                   options: Options)

object Mode extends Enumeration {
  val http, tcp = Value
}

case class Filter(name: String, destination: Backend, acls: Option[HaProxyAcls])

object Acl {
  def apply(definition: String): Acl = Acl(Hash.hexSha1(definition).substring(0, 16), definition)
}

case class Acl(name: String, definition: String)

case class Rewrite(path: String, condition: String)

case class ProxyServer(name: String, lookup: String, unixSock: String, weight: Int)

case class Server(name: String, lookup: String, url: String, weight: Int, checkInterval: Option[Int] = None)

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
