package io.vamp.core.router_driver.haproxy

case class HaProxyConfiguration(pid: Int,
                                statsSocket: String,
                                frontends: List[HaProxyFrontend],
                                backends: List[HaProxyBackend],
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

case class HaProxyFrontend(name: String,
                    bindIp: Option[String],
                    bindPort: Option[Int],
                    mode: HaProxyInterface.Mode.Value,
                    unixSock: Option[String],
                    sockProtocol: Option[String],
                    options: HaProxyOptions,
                    httpQuota: Option[HaProxyHttpQuota],
                    tcpQuota: Option[HaProxyTcpQuota],
                    filters: List[HaProxyFilter],
                    defaultBackend: String) extends HaProxyInterface

case class HaProxyOptions(abortOnClose: Boolean = false,
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

sealed trait HaProxyQuota {
  def sampleWindow: String

  def rate: Int

  def expiryTime: String
}

case class HaProxyHttpQuota(sampleWindow: String, rate: Int, expiryTime: String) extends HaProxyQuota

case class HaProxyTcpQuota(sampleWindow: String, rate: Int, expiryTime: String) extends HaProxyQuota

case class HaProxyFilter(name: String, condition: String, destination: String, negate: Boolean)

case class HaProxyBackend(name: String,
                   mode: HaProxyInterface.Mode.Value,
                   proxyServers: List[HaProxyProxyServer],
                   servers: List[HaProxyServer],
                   options: HaProxyOptions) extends HaProxyInterface

case class HaProxyProxyServer(name: String, unixSock: String, weight: Int)

case class HaProxyServer(name: String, host: String, port: Int, weight: Int, maxConn: Int = 1000, checkInterval: Option[Int] = None)
