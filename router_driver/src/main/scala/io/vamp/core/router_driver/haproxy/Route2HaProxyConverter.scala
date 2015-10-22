package io.vamp.core.router_driver.haproxy

import io.vamp.common.crypto.Hash
import io.vamp.core.router_driver.{ Route, Service }

trait Route2HaProxyConverter {

  def convert(route: Route): HaProxy = HaProxy(frontends(route), backends(route))

  def convert(routes: List[Route]): HaProxy = routes.map(convert).reduce((m1, m2) ⇒ m1.copy(m1.frontends ++ m2.frontends, m1.backends ++ m2.backends))

  private def frontends(implicit route: Route): List[Frontend] = Frontend(
    name = route.name,
    bindIp = Option("0.0.0.0"),
    bindPort = Option(route.port),
    mode = mode,
    unixSock = None,
    sockProtocol = None,
    options = Options(),
    filters = Nil,
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

  private def backends(implicit route: Route): List[Backend] = Backend(
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

  private def mode(implicit route: Route) = if (route.protocol == Interface.Mode.http.toString) Interface.Mode.http else Interface.Mode.tcp

  private def unixSocket(service: Service)(implicit route: Route) = s"/opt/docker/data/${Hash.hexSha1(route.name)}.sock"
}
