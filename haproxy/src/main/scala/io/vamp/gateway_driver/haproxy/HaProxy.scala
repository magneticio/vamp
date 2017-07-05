package io.vamp.gateway_driver.haproxy

import io.vamp.common.util.HashUtil

case class HaProxy(
  frontends:            List[Frontend],
  backends:             List[Backend],
  virtualHostFrontends: List[Frontend],
  virtualHostBackends:  List[Backend]
)

case class Frontend(
  name:           String,
  lookup:         String,
  bindIp:         Option[String],
  bindPort:       Option[Int],
  mode:           Mode.Value,
  unixSock:       Option[String],
  sockProtocol:   Option[String],
  conditions:     List[Condition],
  defaultBackend: Backend
)

case class Backend(
  name:         String,
  lookup:       String,
  mode:         Mode.Value,
  proxyServers: List[ProxyServer],
  servers:      List[Server],
  rewrites:     List[Rewrite],
  sticky:       Boolean,
  balance:      String
)

object Mode extends Enumeration {
  val http, tcp = Value
}

case class Condition(name: String, destination: Backend, acls: Option[HaProxyAcls])

object Acl {
  def apply(definition: String): Acl = Acl(HashUtil.hexSha1(definition).substring(0, 16), definition)
}

case class Acl(name: String, definition: String)

case class Rewrite(path: String, condition: String)

case class ProxyServer(name: String, lookup: String, unixSock: String, weight: Int)

case class Server(name: String, lookup: String, url: Option[String], host: Option[String], port: Option[Int], weight: Int, checkInterval: Option[Int] = None)
