package io.vamp.core.router_driver.haproxy

import io.vamp.common.crypto.Hash
import io.vamp.core.router_driver.{ Filter ⇒ RouteFilter, Route, Service }

trait Route2HaProxyConverter {

  protected val userAgent = "^[uU]ser[-.][aA]gent[ ]?([!])?=[ ]?([a-zA-Z0-9]+)$".r
  protected val host = "^[hH]ost[ ]?([!])?=[ ]?([a-zA-Z0-9.]+)$".r
  protected val cookieContains = "^[cC]ookie (.*) [Cc]ontains (.*)$".r
  protected val hasCookie = "^[Hh]as [Cc]ookie (.*)$".r
  protected val missesCookie = "^[Mm]isses [Cc]ookie (.*)$".r
  protected val headerContains = "^[Hh]eader (.*) [Cc]ontains (.*)$".r
  protected val hasHeader = "^[Hh]as [Hh]eader (.*)$".r
  protected val missesHeader = "^[Mm]isses [Hh]eader (.*)$".r

  def convert(route: Route): HaProxy = HaProxy(frontends(route), backends(route))

  def convert(routes: List[Route]): HaProxy = routes.map(convert).reduce((m1, m2) ⇒ m1.copy(m1.frontends ++ m2.frontends, m1.backends ++ m2.backends))

  protected def frontends(implicit route: Route): List[Frontend] = Frontend(
    name = route.name,
    bindIp = Option("0.0.0.0"),
    bindPort = Option(route.port),
    mode = mode,
    unixSock = None,
    sockProtocol = None,
    options = Options(),
    filters = filters,
    defaultBackend = route.name) :: route.services.map { service ⇒
      Frontend(
        name = s"${route.name}::${service.name}",
        bindIp = None,
        bindPort = None,
        mode = mode,
        unixSock = Option(unixSocket(service)),
        sockProtocol = Option("accept-proxy"),
        options = Options(),
        filters = Nil,
        defaultBackend = s"${route.name}::${service.name}")
    }

  protected def backends(implicit route: Route): List[Backend] = Backend(
    name = route.name,
    mode = mode,
    proxyServers = route.services.map { service ⇒
      ProxyServer(
        name = s"${route.name}::${service.name}",
        unixSock = unixSocket(service),
        weight = service.weight
      )
    },
    servers = Nil,
    options = Options()) :: route.services.map { service ⇒
      Backend(
        name = s"${route.name}::${service.name}",
        mode = mode,
        proxyServers = Nil,
        servers = service.servers.map { server ⇒
          Server(
            name = server.name,
            host = server.host,
            port = server.port,
            weight = 100)
        },
        options = Options())
    }

  protected def filters(implicit route: Route): List[Filter] = route.filters.map(filter)

  protected def filter(filter: RouteFilter)(implicit route: Route): Filter = {
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

    Filter(name, condition, s"${route.name}::${filter.destination}", negate)
  }

  protected def mode(implicit route: Route) = if (route.protocol == Interface.Mode.http.toString) Interface.Mode.http else Interface.Mode.tcp

  protected def unixSocket(service: Service)(implicit route: Route) = s"/opt/docker/data/${Hash.hexSha1(route.name)}.sock"
}
