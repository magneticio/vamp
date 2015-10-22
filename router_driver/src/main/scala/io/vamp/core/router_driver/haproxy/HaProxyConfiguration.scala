package io.vamp.core.router_driver.haproxy

case class HAProxyConfiguration(pid: Int,
                                statsSocket: String,
                                frontends: List[Frontend],
                                backends: List[Backend],
                                errorDir: String)

object HAProxyInterface {

  object Mode extends Enumeration {
    val http, tcp = Value
  }

}

sealed trait HAProxyInterface {
  def name: String

  def mode: HAProxyInterface.Mode.Value
}

case class Frontend(name: String,
                    bindIp: Option[String],
                    bindPort: Option[Int],
                    mode: HAProxyInterface.Mode.Value,
                    unixSock: Option[String],
                    sockProtocol: Option[String],
                    options: Options,
                    httpQuota: Option[HttpQuota],
                    tcpQuota: Option[TcpQuota],
                    filters: List[Filter],
                    defaultBackend: String) extends HAProxyInterface

case class Options(abortOnClose: Boolean,
                   allBackups: Boolean,
                   checkCache: Boolean,
                   forwardFor: Boolean,
                   httpClose: Boolean,
                   httpCheck: Boolean,
                   sslHelloCheck: Boolean,
                   tcpKeepAlive: Boolean,
                   tcpSmartAccept: Boolean,
                   tcpSmartConnect: Boolean,
                   tcpLog: Boolean)

sealed trait Quota {
  def sampleWindow: String

  def rate: Int

  def expiryTime: String
}

case class HttpQuota(sampleWindow: String, rate: Int, expiryTime: String) extends Quota

case class TcpQuota(sampleWindow: String, rate: Int, expiryTime: String) extends Quota

case class Filter(name: String, condition: String, destination: String, negate: Boolean)

case class Backend(name: String,
                   mode: HAProxyInterface.Mode.Value,
                   proxyServers: List[ProxyServer],
                   servers: List[Server],
                   options: Options) extends HAProxyInterface

case class ProxyServer(name: String, unixSock: String, weight: Int)

case class Server(name: String, host: String, port: Int, weight: Int, maxConn: Int, checkInterval: Option[Int])
