package io.vamp.gateway_driver.haproxy

import io.vamp.common.crypto.Hash
import io.vamp.gateway_driver.GatewayMarshaller
import io.vamp.gateway_driver.haproxy.txt.HaProxyConfigurationTemplate
import io.vamp.gateway_driver.model.{ Filter ⇒ GatewayFilter, Gateway, Service }
import io.vamp.model.artifact.Routing

trait HaProxyGatewayMarshaller extends GatewayMarshaller {

  import io.vamp.model.artifact.DefaultFilter._

  override def info: AnyRef = "HAProxy v1.5.x"

  private val socketPath = "/opt/vamp"
  private val pathDelimiter = "::"

  override def marshall(gateways: List[Gateway]) = HaProxyConfigurationTemplate(convert(gateways)).toString().getBytes

  private[haproxy] def convert(gateways: List[Gateway]): HaProxy = {
    gateways.map(convert).reduceOption((m1, m2) ⇒ m1.copy(m1.frontends ++ m2.frontends, m1.backends ++ m2.backends)).getOrElse(HaProxy(Nil, Nil))
  }

  private[haproxy] def convert(gateway: Gateway): HaProxy = {
    val be = backends(gateway)
    HaProxy(frontends(be)(gateway), be)
  }

  private def frontends(backends: List[Backend])(implicit gateway: Gateway): List[Frontend] = {
    def backendFor(name: String) = backends.find(_.name == name).getOrElse(throw new IllegalArgumentException(s"No backend: $name"))

    Frontend(
      name = gateway.name,
      bindIp = Option("0.0.0.0"),
      bindPort = Option(gateway.port),
      mode = mode,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = filters,
      defaultBackend = backendFor(gateway.name)) :: gateway.services.map { service ⇒
        Frontend(
          name = s"${gateway.name}$pathDelimiter${service.name}",
          bindIp = None,
          bindPort = None,
          mode = mode,
          unixSock = Option(unixSocket(service)),
          sockProtocol = Option("accept-proxy"),
          options = Options(),
          filters = Nil,
          defaultBackend = backendFor(s"${gateway.name}$pathDelimiter${service.name}"))
      }
  }

  private def backends(implicit gateway: Gateway): List[Backend] = Backend(
    name = gateway.name,
    mode = mode,
    proxyServers = gateway.services.map { service ⇒
      ProxyServer(
        name = s"${gateway.name}$pathDelimiter${service.name}",
        unixSock = unixSocket(service),
        weight = service.weight
      )
    },
    servers = Nil,
    sticky = gateway.sticky.contains(Routing.Sticky.Service) || gateway.sticky.contains(Routing.Sticky.Instance),
    options = Options()) :: gateway.services.map { service ⇒
      Backend(
        name = s"${gateway.name}$pathDelimiter${service.name}",
        mode = mode,
        proxyServers = Nil,
        servers = service.instances.map { server ⇒
          Server(
            name = server.name,
            host = server.host,
            port = server.port,
            weight = 100)
        },
        sticky = gateway.sticky.contains(Routing.Sticky.Instance),
        options = Options())
    }

  private def filters(implicit gateway: Gateway): List[Filter] = gateway.filters.map(filter)

  private[haproxy] def filter(filter: GatewayFilter)(implicit gateway: Gateway): Filter = {
    val (condition, negate) = filter.condition match {
      case userAgent(n, c)        ⇒ s"hdr_sub(user-agent) ${c.trim}" -> (n == "!")
      case host(n, c)             ⇒ s"hdr_str(host) ${c.trim}" -> (n == "!")
      case cookieContains(c1, c2) ⇒ s"cook_sub(${c1.trim}) ${c2.trim}" -> false
      case hasCookie(c)           ⇒ s"cook(${c.trim}) -m found" -> false
      case missesCookie(c)        ⇒ s"cook_cnt(${c.trim}) eq 0" -> false
      case headerContains(h, c)   ⇒ s"hdr_sub(${h.trim}) ${c.trim}" -> false
      case hasHeader(h)           ⇒ s"hdr_cnt(${h.trim}) gt 0" -> false
      case missesHeader(h)        ⇒ s"hdr_cnt(${h.trim}) eq 0" -> false
      case any                    ⇒ any -> false
    }

    val name = filter.name match {
      case None    ⇒ Hash.hexSha1(condition).substring(0, 16)
      case Some(n) ⇒ n
    }

    Filter(name, condition, s"${gateway.name}$pathDelimiter${filter.destination}", negate)
  }

  private def mode(implicit gateway: Gateway) = if (gateway.protocol == Mode.http.toString) Mode.http else Mode.tcp

  private def unixSocket(service: Service)(implicit gateway: Gateway) = s"$socketPath/${Hash.hexSha1(s"${gateway.name}$pathDelimiter${service.name}")}.sock"
}
