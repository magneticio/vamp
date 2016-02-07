package io.vamp.gateway_driver.haproxy

import com.typesafe.config.ConfigFactory
import io.vamp.gateway_driver.GatewayMarshaller
import io.vamp.gateway_driver.haproxy.txt.HaProxyConfigurationTemplate
import io.vamp.model.artifact._

object HaProxyGatewayMarshaller {

  val version = ConfigFactory.load().getString("vamp.gateway-driver.haproxy.version").trim

  val path: List[String] = "haproxy" :: version :: Nil
}

trait HaProxyGatewayMarshaller extends GatewayMarshaller {

  private val socketPath = "/opt/vamp"

  private val other = "o_"

  private val intermediate = "im_"

  private val aclResolver = new HaProxyAclResolver() {}

  lazy val version = HaProxyGatewayMarshaller.version

  override lazy val path = HaProxyGatewayMarshaller.path

  override lazy val info: AnyRef = s"HAProxy v$version.x"

  def tcpLogFormat: String

  def httpLogFormat: String

  override def marshall(gateways: List[Gateway]): String = HaProxyConfigurationTemplate(convert(gateways)).body.replaceAll("\\\n\\s*\\\n\\s*\\\n", "\n\n")

  private[haproxy] def convert(gateways: List[Gateway]): HaProxy = {
    gateways.map(convert).reduceOption((m1, m2) ⇒ m1.copy(m1.frontends ++ m2.frontends, m1.backends ++ m2.backends)).getOrElse(HaProxy(Nil, Nil, version, tcpLogFormat, httpLogFormat))
  }

  private[haproxy] def convert(gateway: Gateway): HaProxy = backends(gateway) match {
    case backend ⇒ HaProxy(frontends(backend, gateway), backend, version, tcpLogFormat, httpLogFormat)
  }

  private def frontends(implicit backends: List[Backend], gateway: Gateway): List[Frontend] = {

    val gatewayFrontend = Frontend(
      name = GatewayMarshaller.name(gateway),
      lookup = GatewayMarshaller.lookup(gateway),
      bindIp = Option("0.0.0.0"),
      bindPort = Option(gateway.port.number),
      mode = mode,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = filters(),
      defaultBackend = backendFor(other, GatewayMarshaller.lookup(gateway))
    )

    val otherFrontend = Frontend(
      name = s"other ${GatewayMarshaller.name(gateway)}",
      lookup = s"$other${GatewayMarshaller.lookup(gateway)}",
      bindIp = None,
      bindPort = None,
      mode = mode,
      unixSock = Option(unixSocket(s"$other${GatewayMarshaller.lookup(gateway)}")),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backendFor(other, GatewayMarshaller.lookup(gateway))
    )

    val routeFrontends = gateway.routes.map { route ⇒
      Frontend(
        name = GatewayMarshaller.name(gateway, route.path.segments),
        lookup = GatewayMarshaller.lookup(gateway, route.path.segments),
        bindIp = None,
        bindPort = None,
        mode = mode,
        unixSock = Option(unixSocket(GatewayMarshaller.lookup(gateway, route.path.segments))),
        sockProtocol = Option("accept-proxy"),
        options = Options(),
        filters = Nil,
        defaultBackend = backendFor(GatewayMarshaller.lookup(gateway, route.path.segments))
      )
    }

    gatewayFrontend :: otherFrontend :: routeFrontends
  }

  private def backends(implicit gateway: Gateway): List[Backend] = {

    def unsupported(route: Route) = throw new IllegalArgumentException(s"Unsupported route: $route")

    val (imRoutes, otherRoutes) = gateway.routes.partition {
      case route: DefaultRoute ⇒ route.hasRoutingFilters
      case route               ⇒ unsupported(route)
    }

    val imBackends = imRoutes.map {
      case route: DefaultRoute ⇒
        Backend(
          name = s"intermediate ${GatewayMarshaller.name(gateway, route.path.segments)}",
          lookup = s"$intermediate${GatewayMarshaller.lookup(gateway, route.path.segments)}",
          mode = mode,
          proxyServers = ProxyServer(
            name = GatewayMarshaller.name(gateway, route.path.segments),
            lookup = GatewayMarshaller.lookup(gateway, route.path.segments),
            unixSock = unixSocket(GatewayMarshaller.lookup(gateway, route.path.segments)),
            weight = route.weight.get.value
          ) :: ProxyServer(
              name = s"other ${GatewayMarshaller.name(gateway)}",
              lookup = s"$other${GatewayMarshaller.lookup(gateway)}",
              unixSock = unixSocket(s"$other${GatewayMarshaller.lookup(gateway)}"),
              weight = 100 - route.weight.get.value
            ) :: Nil,
          servers = Nil,
          rewrites = Nil,
          sticky = gateway.sticky.contains(Gateway.Sticky.Service) || gateway.sticky.contains(Gateway.Sticky.Instance),
          balance = gateway.defaultBalance,
          options = Options())
      case route ⇒ unsupported(route)
    }

    val otherBackend = Backend(
      name = s"other ${GatewayMarshaller.name(gateway)}",
      lookup = s"$other${GatewayMarshaller.lookup(gateway)}",
      mode = mode,
      proxyServers = otherRoutes.map {
        case route: DefaultRoute ⇒
          ProxyServer(
            name = GatewayMarshaller.name(gateway, route.path.segments),
            lookup = GatewayMarshaller.lookup(gateway, route.path.segments),
            unixSock = unixSocket(GatewayMarshaller.lookup(gateway, route.path.segments)),
            weight = route.weight.get.value
          )
        case route ⇒ unsupported(route)
      },
      servers = Nil,
      rewrites = Nil,
      sticky = gateway.sticky.contains(Gateway.Sticky.Service) || gateway.sticky.contains(Gateway.Sticky.Instance),
      balance = gateway.defaultBalance,
      options = Options()
    )

    val routeBackends = gateway.routes.map {
      case route: DefaultRoute ⇒
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
          balance = route.balance.getOrElse(gateway.defaultBalance),
          options = Options())
      case route ⇒ unsupported(route)
    }

    otherBackend :: imBackends ++ routeBackends
  }

  private def filters()(implicit backends: List[Backend], gateway: Gateway): List[Filter] = gateway.routes.flatMap {
    case route: DefaultRoute ⇒ if (route.filters.nonEmpty) filter(route) :: Nil else Nil
    case _                   ⇒ Nil
  }

  private[haproxy] def filter(route: DefaultRoute)(implicit backends: List[Backend], gateway: Gateway): Filter = {
    route.filters.filter(_.isInstanceOf[DefaultFilter]).map(_.asInstanceOf[DefaultFilter].condition) match {
      case conditions ⇒
        backendFor(intermediate, GatewayMarshaller.lookup(gateway, route.path.segments)) match {
          case backend ⇒ Filter(backend.lookup, backend, aclResolver.resolve(conditions))
        }
    }
  }

  private def rewrites(route: DefaultRoute): List[Rewrite] = route.rewrites.flatMap {
    case PathRewrite(_, p, c) ⇒ Rewrite(p, if (c.matches("^\\s*\\{.*\\}\\s*$")) c else s"{ $c }") :: Nil
    case _                    ⇒ Nil
  }

  private def backendFor(lookup: String*)(implicit backends: List[Backend]): Backend = lookup.mkString match {
    case l ⇒ backends.find(_.lookup == l).getOrElse(throw new IllegalArgumentException(s"No backend: $lookup"))
  }

  private def mode(implicit gateway: Gateway) = if (gateway.port.`type` == Port.Type.Http) Mode.http else Mode.tcp

  private def unixSocket(id: String)(implicit gateway: Gateway) = s"$socketPath/$id.sock"
}
