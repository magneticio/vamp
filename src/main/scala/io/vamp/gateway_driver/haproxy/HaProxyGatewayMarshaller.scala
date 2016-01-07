package io.vamp.gateway_driver.haproxy

import io.vamp.gateway_driver.GatewayMarshaller
import io.vamp.gateway_driver.haproxy.txt.HaProxyConfigurationTemplate
import io.vamp.model.artifact._

object HaProxyGatewayMarshaller {
  val path: List[String] = "haproxy" :: "1.6" :: Nil
}

trait HaProxyGatewayMarshaller extends GatewayMarshaller {

  import io.vamp.model.artifact.DefaultFilter._

  private val socketPath = "/opt/vamp"

  override val path = HaProxyGatewayMarshaller.path

  override def info: AnyRef = "HAProxy v1.6.x"

  def tcpLogFormat: String

  def httpLogFormat: String

  override def marshall(gateways: List[Gateway]): String = HaProxyConfigurationTemplate(convert(gateways)).body.replaceAll("\\\n\\s*\\\n\\s*\\\n", "\n\n")

  private[haproxy] def convert(gateways: List[Gateway]): HaProxy = {
    gateways.map(convert).reduceOption((m1, m2) ⇒ m1.copy(m1.frontends ++ m2.frontends, m1.backends ++ m2.backends)).getOrElse(HaProxy(Nil, Nil, tcpLogFormat, httpLogFormat))
  }

  private[haproxy] def convert(gateway: Gateway): HaProxy = backends(gateway) match {
    case backend ⇒ HaProxy(frontends(backend)(gateway), backend, tcpLogFormat, httpLogFormat)
  }

  private def frontends(backends: List[Backend])(implicit gateway: Gateway): List[Frontend] = Frontend(
    name = GatewayMarshaller.name(gateway),
    lookup = GatewayMarshaller.lookup(gateway),
    bindIp = Option("0.0.0.0"),
    bindPort = Option(gateway.port.number),
    mode = mode,
    unixSock = None,
    sockProtocol = None,
    options = Options(),
    filters = filters(backends),
    defaultBackend = backendFor(backends, GatewayMarshaller.lookup(gateway))) :: gateway.routes.map { route ⇒
      Frontend(
        name = GatewayMarshaller.name(gateway, route.path.segments),
        lookup = GatewayMarshaller.lookup(gateway, route.path.segments),
        bindIp = None,
        bindPort = None,
        mode = mode,
        unixSock = Option(unixSocket(route)),
        sockProtocol = Option("accept-proxy"),
        options = Options(),
        filters = Nil,
        defaultBackend = backendFor(backends, GatewayMarshaller.lookup(gateway, route.path.segments)))
    }

  private def backends(implicit gateway: Gateway): List[Backend] = Backend(
    name = GatewayMarshaller.name(gateway),
    lookup = GatewayMarshaller.lookup(gateway),
    mode = mode,
    proxyServers = gateway.routes.map {
      case route: AbstractRoute ⇒
        ProxyServer(
          name = GatewayMarshaller.name(gateway, route.path.segments),
          lookup = GatewayMarshaller.lookup(gateway, route.path.segments),
          unixSock = unixSocket(route),
          weight = route.weight.get.value
        )
      case route ⇒ throw new IllegalArgumentException(s"Unsupported route: $route")
    },
    servers = Nil,
    rewrites = Nil,
    sticky = gateway.sticky.contains(Gateway.Sticky.Service) || gateway.sticky.contains(Gateway.Sticky.Instance),
    options = Options()) :: gateway.routes.map {
      case route: DeployedRoute ⇒
        Backend(
          name = GatewayMarshaller.name(gateway, route.path.segments),
          lookup = GatewayMarshaller.lookup(gateway, route.path.segments),
          mode = mode,
          proxyServers = Nil,
          servers = route.targets.map { instance ⇒
            Server(
              name = GatewayMarshaller.name(instance),
              lookup = GatewayMarshaller.lookup(instance),
              host = instance.host,
              port = instance.port,
              weight = 100)
          },
          rewrites = rewrites(route),
          sticky = gateway.sticky.contains(Gateway.Sticky.Instance),
          options = Options())
      case route ⇒ throw new IllegalArgumentException(s"Unsupported route: $route")
    }

  private def filters(backends: List[Backend])(implicit gateway: Gateway): List[Filter] = gateway.routes.flatMap {
    case route: AbstractRoute ⇒ if (route.filters.nonEmpty) filter(backends, route) :: Nil else Nil
    case _                    ⇒ Nil
  }

  private[haproxy] def filter(backends: List[Backend], route: AbstractRoute)(implicit gateway: Gateway): Filter = {
    val conditions = route.filters.filter(_.isInstanceOf[DefaultFilter]).map(_.asInstanceOf[DefaultFilter].condition).flatMap {
      case userAgent(n, c)        ⇒ Condition(s"hdr_sub(user-agent) ${c.trim}", n == "!") :: Nil
      case host(n, c)             ⇒ Condition(s"hdr_str(host) ${c.trim}", n == "!") :: Nil
      case cookieContains(c1, c2) ⇒ Condition(s"cook_sub(${c1.trim}) ${c2.trim}") :: Nil
      case hasCookie(c)           ⇒ Condition(s"cook(${c.trim}) -m found") :: Nil
      case missesCookie(c)        ⇒ Condition(s"cook_cnt(${c.trim}) eq 0") :: Nil
      case headerContains(h, c)   ⇒ Condition(s"hdr_sub(${h.trim}) ${c.trim}") :: Nil
      case hasHeader(h)           ⇒ Condition(s"hdr_cnt(${h.trim}) gt 0") :: Nil
      case missesHeader(h)        ⇒ Condition(s"hdr_cnt(${h.trim}) eq 0") :: Nil
      case rewrite(p, c)          ⇒ Nil
      case any                    ⇒ Condition(any) :: Nil
    }

    backendFor(backends, GatewayMarshaller.lookup(gateway, route.path.segments)) match {
      case backend ⇒ Filter(backend.lookup, backend, conditions)
    }
  }

  private def rewrites(route: AbstractRoute): List[Rewrite] = route.filters.filter(_.isInstanceOf[DefaultFilter]).map(_.asInstanceOf[DefaultFilter].condition).flatMap {
    case rewrite(p, c) ⇒ Rewrite(p, if (c.matches("^\\s*\\{.*\\}\\s*$")) c else s"{ $c }") :: Nil
    case _             ⇒ Nil
  }

  private def backendFor(backends: List[Backend], lookup: String): Backend = backends.find(_.lookup == lookup).getOrElse(throw new IllegalArgumentException(s"No backend: $lookup"))

  private def mode(implicit gateway: Gateway) = if (gateway.port.`type` == Port.Type.Http) Mode.http else Mode.tcp

  private def unixSocket(route: Route)(implicit gateway: Gateway) = s"$socketPath/${GatewayMarshaller.lookup(gateway, route.path.segments)}.sock"
}
