package io.vamp.core.router_driver.haproxy

import io.vamp.common.crypto.Hash
import io.vamp.core.router_driver.{ Route, Service }

import scala.language.implicitConversions

object Route2HaProxyConverter extends Route2HaProxyConverter {

  implicit def route2haproxy(route: Route): HaProxyModel = convert(route)
}

trait Route2HaProxyConverter {

  def convert(route: Route): HaProxyModel = HaProxyModel(frontends(route), backends(route))

  def convert(routes: List[Route]): HaProxyModel = routes.map(convert).reduce((m1, m2) ⇒ m1.copy(m1.frontends ++ m2.frontends, m1.backends ++ m2.backends))

  private def frontends(implicit route: Route): List[Frontend] = Frontend(
    name = route.name,
    bindIp = Option("0.0.0.0"),
    bindPort = Option(route.port),
    mode = mode,
    unixSock = None,
    sockProtocol = None,
    options = HaProxyOptions(),
    filters = Nil,
    defaultBackend = route.name) :: route.services.map { service ⇒
      Frontend(
        name = s"${route.name}::${service.name}",
        bindIp = None,
        bindPort = None,
        mode = mode,
        unixSock = Option(unixSocket(service)),
        sockProtocol = Option("accept-proxy"),
        options = HaProxyOptions(),
        filters = Nil,
        defaultBackend = s"${route.name}::${service.name}")
    }

  private def backends(implicit route: Route): List[Backend] = Backend(
    name = route.name,
    mode = mode,
    proxyServers = route.services.map { service ⇒
      HaProxyProxyServer(
        name = s"${route.name}::${service.name}",
        unixSock = unixSocket(service),
        weight = service.weight
      )
    },
    servers = Nil,
    options = HaProxyOptions()) :: route.services.map { service ⇒
      Backend(
        name = s"${route.name}::${service.name}",
        mode = mode,
        proxyServers = Nil,
        servers = route.services.flatMap { service ⇒
          service.servers.map { server ⇒
            HaProxyServer(
              name = server.name,
              host = server.host,
              port = server.port,
              weight = service.weight)
          }
        },
        options = HaProxyOptions())
    }

  private def mode(implicit route: Route) = if (route.protocol == HaProxyInterface.Mode.http.toString) HaProxyInterface.Mode.http else HaProxyInterface.Mode.tcp

  private def unixSocket(service: Service)(implicit route: Route) = s"/opt/docker/data/${Hash.hexSha1(route.name)}.sock"
}
