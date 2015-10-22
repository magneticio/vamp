package io.vamp.core.router_driver.haproxy.txt

import io.vamp.core.router_driver.haproxy.{ Server ⇒ HaProxyServer, _ }
import io.vamp.core.router_driver.{ Route, Server, Service }
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{ FlatSpec, Matchers }

import scala.io.Source
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class HaProxyConfigurationTemplateSpec extends FlatSpec with Matchers with Route2HaProxyConverter {

  it should "serialize A/B services to HAProxy configuration" in {
    val model = convert(List(
      Route(
        name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_backend_8080",
        port = 33003,
        protocol = "http",
        filters = Nil,
        services = List(
          Service(
            name = "sava-backend:1.3.0",
            weight = 100,
            servers = List(
              Server(
                name = "57c4e3d2cbb8f0db907f5e16ceed9a4241d7e117",
                host = "192.168.99.100",
                port = 32770))
          ))),
      Route(
        name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_sava_8080",
        port = 33002,
        protocol = "http",
        filters = Nil,
        services = List(
          Service(
            name = "sava-frontend:1.3.0",
            weight = 100,
            servers = List(
              Server(
                name = "f1638245acf2ebe6db56984a85b48f6db8c74607",
                host = "192.168.99.100",
                port = 32771))
          ))),
      Route(
        name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_9050",
        port = 9050,
        protocol = "http",
        filters = Nil,
        services = Service(
          name = "sava.port",
          weight = 100,
          servers = Server(
            name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_9050",
            host = "192.168.99.100",
            port = 33002) :: Nil
        ) :: Nil)
    ))

    model shouldBe HaProxy(List(
      Frontend(
        name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_backend_8080",
        bindIp = Option("0.0.0.0"),
        bindPort = Option(33003),
        mode = Interface.Mode.http,
        unixSock = None,
        sockProtocol = None,
        options = Options(),
        filters = Nil,
        defaultBackend = "d5c3c612-6fb3-41e5-8023-292ce3c74924_backend_8080"),
      Frontend(
        name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_backend_8080::sava-backend:1.3.0",
        bindIp = None,
        bindPort = None,
        mode = Interface.Mode.http,
        unixSock = Option("/opt/docker/data/672003d66ed7c9b0c8a02cac65811b44457c2d95.sock"),
        sockProtocol = Option("accept-proxy"),
        options = Options(),
        filters = Nil,
        defaultBackend = "d5c3c612-6fb3-41e5-8023-292ce3c74924_backend_8080::sava-backend:1.3.0"),
      Frontend(
        name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_sava_8080",
        bindIp = Option("0.0.0.0"),
        bindPort = Option(33002),
        mode = Interface.Mode.http,
        unixSock = None,
        sockProtocol = None,
        options = Options(),
        filters = Nil,
        defaultBackend = "d5c3c612-6fb3-41e5-8023-292ce3c74924_sava_8080"),
      Frontend(
        name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_sava_8080::sava-frontend:1.3.0",
        bindIp = None,
        bindPort = None,
        mode = Interface.Mode.http,
        unixSock = Option("/opt/docker/data/4b4471b11e0d48d599f3788a43b31c37c350ecdc.sock"),
        sockProtocol = Option("accept-proxy"),
        options = Options(),
        filters = Nil,
        defaultBackend = "d5c3c612-6fb3-41e5-8023-292ce3c74924_sava_8080::sava-frontend:1.3.0"),
      Frontend(
        name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_9050",
        bindIp = Option("0.0.0.0"),
        bindPort = Option(9050),
        mode = Interface.Mode.http,
        unixSock = None,
        sockProtocol = None,
        options = Options(),
        filters = Nil,
        defaultBackend = "d5c3c612-6fb3-41e5-8023-292ce3c74924_9050"),
      Frontend(
        name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_9050::sava.port",
        bindIp = None,
        bindPort = None,
        mode = Interface.Mode.http,
        unixSock = Option("/opt/docker/data/599bb91c23e5f09b6cd656389aca83d03543c9ef.sock"),
        sockProtocol = Option("accept-proxy"),
        options = Options(),
        filters = Nil,
        defaultBackend = "d5c3c612-6fb3-41e5-8023-292ce3c74924_9050::sava.port")
    ),
      List(
        Backend(
          name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_backend_8080",
          mode = Interface.Mode.http,
          proxyServers = List(
            ProxyServer(
              name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_backend_8080::sava-backend:1.3.0",
              unixSock = "/opt/docker/data/672003d66ed7c9b0c8a02cac65811b44457c2d95.sock",
              weight = 100
            )),
          servers = Nil,
          options = Options()),
        Backend(
          name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_backend_8080::sava-backend:1.3.0",
          mode = Interface.Mode.http,
          proxyServers = Nil,
          servers = List(
            HaProxyServer(
              name = "57c4e3d2cbb8f0db907f5e16ceed9a4241d7e117",
              host = "192.168.99.100",
              port = 32770,
              weight = 100)),
          options = Options()),
        Backend(
          name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_sava_8080",
          mode = Interface.Mode.http,
          proxyServers = List(
            ProxyServer(
              name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_sava_8080::sava-frontend:1.3.0",
              unixSock = "/opt/docker/data/4b4471b11e0d48d599f3788a43b31c37c350ecdc.sock",
              weight = 100
            )),
          servers = Nil,
          options = Options()),
        Backend(
          name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_sava_8080::sava-frontend:1.3.0",
          mode = Interface.Mode.http,
          proxyServers = Nil,
          servers = List(
            HaProxyServer(
              name = "f1638245acf2ebe6db56984a85b48f6db8c74607",
              host = "192.168.99.100",
              port = 32771,
              weight = 100)),
          options = Options()),
        Backend(
          name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_9050",
          mode = Interface.Mode.http,
          proxyServers = ProxyServer(
            name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_9050::sava.port",
            unixSock = "/opt/docker/data/599bb91c23e5f09b6cd656389aca83d03543c9ef.sock",
            weight = 100
          ) :: Nil,
          servers = Nil,
          options = Options()),
        Backend(
          name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_9050::sava.port",
          mode = Interface.Mode.http,
          proxyServers = Nil,
          servers = HaProxyServer(
            name = "d5c3c612-6fb3-41e5-8023-292ce3c74924_9050",
            host = "192.168.99.100",
            port = 33002,
            weight = 100) :: Nil,
          options = Options())
      ))

    compare(HaProxyConfigurationTemplate(HaProxyConfiguration(
      pidFile = "/opt/docker/data/haproxy-private.pid",
      statsSocket = "/opt/docker/data/haproxy.stats.sock",
      frontends = model.frontends,
      backends = model.backends,
      errorDir = "/opt/docker/configuration/error_pages")
    ).toString(), "configuration_6.txt")
  }

  private def compare(config: String, resource: String) = {

    def normalize(string: String): Array[String] = string.split('\n').map(_.trim).filter(_.nonEmpty).filterNot(_.startsWith("#")).map(_.replaceAll("\\s+", " "))

    val actual = normalize(config)
    val expected = normalize(Source.fromURL(getClass.getResource(resource)).mkString)

    actual.length shouldBe expected.length

    actual.zip(expected).foreach { line ⇒
      line._1 shouldBe line._2
    }
  }
}
