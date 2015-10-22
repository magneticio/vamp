package io.vamp.core.router_driver.haproxy

case class HaProxyConfiguration(pidFile: String, statsSocket: String, frontends: List[Frontend], backends: List[Backend], errorDir: String)

object HaProxyInterface {

  object Mode extends Enumeration {
    val http, tcp = Value
  }

}

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

case class HaProxyProxyServer(name: String, unixSock: String, weight: Int)

case class HaProxyServer(name: String, host: String, port: Int, weight: Int, maxConn: Int = 1000, checkInterval: Option[Int] = None)
