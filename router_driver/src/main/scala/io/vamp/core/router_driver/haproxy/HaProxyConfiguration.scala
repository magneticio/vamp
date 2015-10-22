package io.vamp.core.router_driver.haproxy

case class HaProxyConfiguration(pid: Int,
                                statsSocket: String,
                                frontends: List[Frontend],
                                backends: List[Backend],
                                errorDir: String)

object HaProxyInterface {

  object Mode extends Enumeration {
    val http, tcp = Value
  }

}

sealed trait HaProxyInterface {
  def name: String

  def mode: HaProxyInterface.Mode.Value
}

case class Frontend(name: String,
                    bindIp: Option[String],
                    bindPort: Option[Int],
                    mode: HaProxyInterface.Mode.Value,
                    unixSock: Option[String],
                    sockProtocol: Option[String],
                    options: Options,
                    httpQuota: Option[HttpQuota],
                    tcpQuota: Option[TcpQuota],
                    filters: List[Filter],
                    defaultBackend: String) extends HaProxyInterface

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
                   mode: HaProxyInterface.Mode.Value,
                   proxyServers: List[ProxyServer],
                   servers: List[Server],
                   options: Options) extends HaProxyInterface

case class ProxyServer(name: String, unixSock: String, weight: Int)

case class Server(name: String, host: String, port: Int, weight: Int, maxConn: Int, checkInterval: Option[Int])
