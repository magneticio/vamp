package io.vamp.gateway_driver.haproxy

import io.vamp.common.config.Config
import io.vamp.gateway_driver.GatewayMarshaller
import io.vamp.model.artifact._

import scala.language.postfixOps

object HaProxyGatewayMarshaller {

  val version = Config.string("vamp.gateway-driver.haproxy.version").trim

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

  def haProxyConfig: HaProxyConfig

  override def marshall(gateways: List[Gateway]): String = marshall(convert(gateways))

  private[haproxy] def marshall(haProxy: HaProxy): String

  private[haproxy] def convert(gateways: List[Gateway]): HaProxy = {
    gateways.map(convert).reduceOption((m1, m2) ⇒ m1.copy(
      frontends = m1.frontends ++ m2.frontends,
      backends = m1.backends ++ m2.backends,
      virtualHostFrontends = m1.virtualHostFrontends ++ m2.virtualHostFrontends,
      virtualHostBackends = m1.virtualHostBackends ++ m2.virtualHostBackends
    )).getOrElse(
      HaProxy(version, Nil, Nil, Nil, Nil, haProxyConfig)
    )
  }

  private[haproxy] def convert(gateway: Gateway): HaProxy = {
    val be = backends(gateway)
    val fe = frontends(be, gateway)

    val vbe = virtualHostsBackends(gateway)
    val vfe = virtualHostsFrontends(vbe, gateway)

    HaProxy(version, fe, be, vfe, vbe, haProxyConfig)
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

    val imRoutes = gateway.routes.filter {
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
            weight = route.filterStrength.get.value
          ) :: ProxyServer(
              name = s"other ${GatewayMarshaller.name(gateway)}",
              lookup = s"$other${GatewayMarshaller.lookup(gateway)}",
              unixSock = unixSocket(s"$other${GatewayMarshaller.lookup(gateway)}"),
              weight = 100 - route.filterStrength.get.value
            ) :: Nil,
          servers = Nil,
          rewrites = Nil,
          sticky = gateway.sticky.contains(Gateway.Sticky.Route) || gateway.sticky.contains(Gateway.Sticky.Instance),
          balance = gateway.defaultBalance,
          options = Options())
      case route ⇒ unsupported(route)
    }

    val otherBackend = Backend(
      name = s"other ${GatewayMarshaller.name(gateway)}",
      lookup = s"$other${GatewayMarshaller.lookup(gateway)}",
      mode = mode,
      proxyServers = gateway.routes.map {
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
      sticky = gateway.sticky.contains(Gateway.Sticky.Route) || gateway.sticky.contains(Gateway.Sticky.Instance),
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
          servers = route.targets.map {
            case internal: InternalRouteTarget ⇒
              Server(
                name = GatewayMarshaller.name(internal),
                lookup = GatewayMarshaller.lookup(internal),
                url = s"${internal.host}:${internal.port}",
                weight = 100
              )
            case external: ExternalRouteTarget ⇒
              Server(
                name = GatewayMarshaller.name(external),
                lookup = GatewayMarshaller.lookup(external),
                url = external.url,
                weight = 100
              )
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

  private def virtualHostsFrontends(implicit backends: List[Backend], gateway: Gateway): List[Frontend] = {
    gateway.virtualHosts.map { virtualHost ⇒
      val acl = Acl(s"hdr(host) -i $virtualHost")
      Frontend(
        name = GatewayMarshaller.name(gateway),
        lookup = GatewayMarshaller.lookup(gateway),
        bindIp = None,
        bindPort = None,
        mode = mode,
        unixSock = None,
        sockProtocol = None,
        options = Options(),
        filters = Filter(
          GatewayMarshaller.lookup(gateway),
          backendFor(GatewayMarshaller.lookup(gateway)),
          Option(HaProxyAcls(acl :: Nil, Option(acl.name)))
        ) :: Nil,
        defaultBackend = backendFor(GatewayMarshaller.lookup(gateway))
      )
    }
  }

  private def virtualHostsBackends(implicit gateway: Gateway): List[Backend] = gateway.virtualHosts.nonEmpty match {
    case true ⇒
      Backend(
        name = GatewayMarshaller.name(gateway),
        lookup = GatewayMarshaller.lookup(gateway),
        mode = mode,
        proxyServers = Nil,
        servers = Server(
          name = GatewayMarshaller.name(gateway),
          lookup = GatewayMarshaller.lookup(gateway),
          url = s"127.0.0.1:${gateway.port.number}",
          weight = 100
        ) :: Nil,
        rewrites = Nil,
        sticky = false,
        balance = "",
        options = Options()
      ) :: Nil

    case false ⇒ Nil
  }
}
