package io.vamp.gateway_driver.haproxy

case class HaProxy(frontends: List[Frontend], backends: List[Backend])

object Flatten {
  def flatten(string: String) = string.replaceAll("[^\\p{L}\\d]", "_")
}

trait FlattenName {
  def name: String

  def flattenName = Flatten.flatten(name)
}

case class Frontend(name: String,
                    bindIp: Option[String],
                    bindPort: Option[Int],
                    mode: Mode.Value,
                    unixSock: Option[String],
                    sockProtocol: Option[String],
                    options: Options,
                    filters: List[Filter],
                    defaultBackend: Backend) extends FlattenName

case class Backend(name: String,
                   mode: Mode.Value,
                   proxyServers: List[ProxyServer],
                   servers: List[Server],
                   options: Options) extends FlattenName

object Mode extends Enumeration {
  val http, tcp = Value
}

case class Filter(name: String, condition: String, destination: String, negate: Boolean = false) {
  lazy val flattenDestination = Flatten.flatten(destination)
}

case class ProxyServer(name: String, unixSock: String, weight: Int) extends FlattenName

case class Server(name: String, host: String, port: Int, weight: Int, maxConn: Int = 1000, checkInterval: Option[Int] = None) extends FlattenName

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
