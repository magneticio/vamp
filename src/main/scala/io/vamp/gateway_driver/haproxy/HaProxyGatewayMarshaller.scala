package io.vamp.gateway_driver.haproxy

import io.vamp.common.spi.ClassMapper
import io.vamp.common.util.ObjectUtil._
import io.vamp.gateway_driver.GatewayMarshaller
import io.vamp.model.artifact._
import org.jtwig.{ JtwigModel, JtwigTemplate }

import scala.language.postfixOps

class HaProxyGatewayMarshallerMapper extends ClassMapper {
  val name = "haproxy"
  val clazz = classOf[HaProxyGatewayMarshaller]
}

class HaProxyGatewayMarshaller extends GatewayMarshaller {

  private val other = "o_"

  private val intermediate = "im_"

  private val aclResolver = new HaProxyAclResolver() {}

  override val `type` = "haproxy"

  override lazy val info: AnyRef = Map("type" → `type`)

  override def marshall(gateways: List[Gateway], template: String): String = marshall(convert(gateways), template)

  private[haproxy] def marshall(haProxy: HaProxy, template: String): String = {
    val model = JtwigModel.newModel().`with`("haproxy", (unwrap andThen javaObject)(haProxy))
    JtwigTemplate.inlineTemplate(template).render(model)
  }

  private[haproxy] def convert(gateways: List[Gateway]): HaProxy = {
    gateways.map(convert).reduceOption((m1, m2) ⇒ m1.copy(
      frontends = m1.frontends ++ m2.frontends,
      backends = m1.backends ++ m2.backends,
      virtualHostFrontends = m1.virtualHostFrontends ++ m2.virtualHostFrontends,
      virtualHostBackends = m1.virtualHostBackends ++ m2.virtualHostBackends
    )).getOrElse(
      HaProxy(Nil, Nil, Nil, Nil)
    )
  }

  private[haproxy] def convert(gateway: Gateway): HaProxy = {
    val be = backends(gateway)
    val fe = frontends(be, gateway)

    val vbe = virtualHostsBackends(gateway)
    val vfe = virtualHostsFrontends(vbe, gateway)

    HaProxy(fe, be, vfe, vbe)
  }

  private def frontends(implicit backends: List[Backend], gateway: Gateway): List[Frontend] = {

    val gatewayFrontend = Frontend(
      name = GatewayLookup.name(gateway),
      lookup = GatewayLookup.lookup(gateway),
      bindIp = Option("0.0.0.0"),
      bindPort = Option(gateway.port.number),
      mode = mode,
      unixSock = None,
      sockProtocol = None,
      conditions = conditions(),
      defaultBackend = backendFor(other, GatewayLookup.lookup(gateway))
    )

    val otherFrontend = Frontend(
      name = s"other ${GatewayLookup.name(gateway)}",
      lookup = s"$other${GatewayLookup.lookup(gateway)}",
      bindIp = None,
      bindPort = None,
      mode = mode,
      unixSock = Option(unixSocket(s"$other${GatewayLookup.lookup(gateway)}")),
      sockProtocol = Option("accept-proxy"),
      conditions = Nil,
      defaultBackend = backendFor(other, GatewayLookup.lookup(gateway))
    )

    val routeFrontends = gateway.routes.map { route ⇒
      Frontend(
        name = GatewayLookup.name(gateway, route.path.segments),
        lookup = GatewayLookup.lookup(gateway, route.path.segments),
        bindIp = None,
        bindPort = None,
        mode = mode,
        unixSock = Option(unixSocket(GatewayLookup.lookup(gateway, route.path.segments))),
        sockProtocol = Option("accept-proxy"),
        conditions = Nil,
        defaultBackend = backendFor(GatewayLookup.lookup(gateway, route.path.segments))
      )
    }

    gatewayFrontend :: otherFrontend :: routeFrontends
  }

  private def backends(implicit gateway: Gateway): List[Backend] = {

    def unsupported(route: Route) = throw new IllegalArgumentException(s"Unsupported route: $route")

    val imRoutes = gateway.routes.filter {
      case route: DefaultRoute ⇒ route.definedCondition
      case route               ⇒ unsupported(route)
    }

    val imBackends = imRoutes.map {
      case route: DefaultRoute ⇒
        Backend(
          name = s"intermediate ${GatewayLookup.name(gateway, route.path.segments)}",
          lookup = s"$intermediate${GatewayLookup.lookup(gateway, route.path.segments)}",
          mode = mode,
          proxyServers = ProxyServer(
          name = GatewayLookup.name(gateway, route.path.segments),
          lookup = GatewayLookup.lookup(gateway, route.path.segments),
          unixSock = unixSocket(GatewayLookup.lookup(gateway, route.path.segments)),
          weight = route.conditionStrength.get.value
        ) :: ProxyServer(
            name = s"other ${GatewayLookup.name(gateway)}",
            lookup = s"$other${GatewayLookup.lookup(gateway)}",
            unixSock = unixSocket(s"$other${GatewayLookup.lookup(gateway)}"),
            weight = 100 - route.conditionStrength.get.value
          ) :: Nil,
          servers = Nil,
          rewrites = Nil,
          sticky = gateway.sticky.contains(Gateway.Sticky.Route) || gateway.sticky.contains(Gateway.Sticky.Instance),
          balance = gateway.defaultBalance
        )
      case route ⇒ unsupported(route)
    }

    val otherBackend = Backend(
      name = s"other ${GatewayLookup.name(gateway)}",
      lookup = s"$other${GatewayLookup.lookup(gateway)}",
      mode = mode,
      proxyServers = gateway.routes.map {
        case route: DefaultRoute ⇒
          ProxyServer(
            name = GatewayLookup.name(gateway, route.path.segments),
            lookup = GatewayLookup.lookup(gateway, route.path.segments),
            unixSock = unixSocket(GatewayLookup.lookup(gateway, route.path.segments)),
            weight = route.weight.get.value
          )
        case route ⇒ unsupported(route)
      },
      servers = Nil,
      rewrites = Nil,
      sticky = gateway.sticky.contains(Gateway.Sticky.Route) || gateway.sticky.contains(Gateway.Sticky.Instance),
      balance = gateway.defaultBalance
    )

    val routeBackends = gateway.routes.map {
      case route: DefaultRoute ⇒
        Backend(
          name = GatewayLookup.name(gateway, route.path.segments),
          lookup = GatewayLookup.lookup(gateway, route.path.segments),
          mode = mode,
          proxyServers = Nil,
          servers = route.targets.map {
            case internal: InternalRouteTarget ⇒
              Server(
                name = GatewayLookup.name(internal),
                lookup = GatewayLookup.lookup(internal),
                url = None,
                host = internal.host,
                port = Option(internal.port),
                weight = 100
              )
            case external: ExternalRouteTarget ⇒
              Server(
                name = GatewayLookup.name(external),
                lookup = GatewayLookup.lookup(external),
                url = Option(external.url),
                host = None,
                port = None,
                weight = 100
              )
          },
          rewrites = rewrites(route),
          sticky = gateway.sticky.contains(Gateway.Sticky.Instance),
          balance = route.balance.getOrElse(gateway.defaultBalance)
        )
      case route ⇒ unsupported(route)
    }

    otherBackend :: imBackends ++ routeBackends
  }

  private def conditions()(implicit backends: List[Backend], gateway: Gateway): List[Condition] = gateway.routes.collect {
    case route: DefaultRoute if route.condition.nonEmpty ⇒ filter(route)
  } flatten

  private[haproxy] def filter(route: DefaultRoute)(implicit backends: List[Backend], gateway: Gateway): Option[Condition] = {
    route.condition.filter(_.isInstanceOf[DefaultCondition]).map(_.asInstanceOf[DefaultCondition].definition) map {
      condition ⇒
        backendFor(intermediate, GatewayLookup.lookup(gateway, route.path.segments)) match {
          case backend ⇒ Condition(backend.lookup, backend, aclResolver.resolve(condition))
        }
    }
  }

  private def rewrites(route: DefaultRoute): List[Rewrite] = route.rewrites.collect {
    case PathRewrite(_, p, c) ⇒ Rewrite(p, if (c.matches("^\\s*\\{.*\\}\\s*$")) c else s"{ $c }")
  }

  private def backendFor(lookup: String*)(implicit backends: List[Backend]): Backend = lookup.mkString match {
    case l ⇒ backends.find(_.lookup == l).getOrElse(throw new IllegalArgumentException(s"No backend: $lookup"))
  }

  private def mode(implicit gateway: Gateway) = if (gateway.port.`type` == Port.Type.Http) Mode.http else Mode.tcp

  private def unixSocket(id: String)(implicit gateway: Gateway) = s"$id.sock"

  private def virtualHostsFrontends(implicit backends: List[Backend], gateway: Gateway): List[Frontend] = {
    gateway.virtualHosts.map { virtualHost ⇒
      val acl = Acl(s"hdr(host) -i $virtualHost")
      Frontend(
        name = GatewayLookup.name(gateway),
        lookup = GatewayLookup.lookup(gateway),
        bindIp = None,
        bindPort = None,
        mode = mode,
        unixSock = None,
        sockProtocol = None,
        conditions = Condition(
          GatewayLookup.lookup(gateway),
          backendFor(GatewayLookup.lookup(gateway)),
          Option(HaProxyAcls(acl :: Nil, Option(acl.name)))
        ) :: Nil,
        defaultBackend = backendFor(GatewayLookup.lookup(gateway))
      )
    }
  }

  private def virtualHostsBackends(implicit gateway: Gateway): List[Backend] = {
    if (gateway.virtualHosts.nonEmpty) {
      Backend(
        name = GatewayLookup.name(gateway),
        lookup = GatewayLookup.lookup(gateway),
        mode = mode,
        proxyServers = Nil,
        servers = Server(
          name = GatewayLookup.name(gateway),
          lookup = GatewayLookup.lookup(gateway),
          url = None,
          host = None,
          port = Option(gateway.port.number),
          weight = 100
        ) :: Nil,
        rewrites = Nil,
        sticky = false,
        balance = ""
      ) :: Nil
    }
    else Nil
  }
}
