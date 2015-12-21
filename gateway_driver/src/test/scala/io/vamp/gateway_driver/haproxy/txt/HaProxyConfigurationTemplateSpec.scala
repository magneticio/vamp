package io.vamp.gateway_driver.haproxy.txt

import io.vamp.gateway_driver.haproxy.{ Filter ⇒ HaProxyFilter, Server ⇒ HaProxyServer, _ }
import io.vamp.model.artifact._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{ FlatSpec, Informer, Matchers }

import scala.io.Source
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class HaProxyConfigurationTemplateSpec extends FlatSpec with Matchers with HaProxyGatewayMarshaller {

  override def info: Informer = super[FlatSpec].info

  "HaProxyConfiguration" should "be serialized to valid HAProxy configuration" in {

    val options = Options(
      abortOnClose = true,
      allBackups = true,
      checkCache = true,
      forwardFor = true,
      httpClose = true,
      httpCheck = true,
      sslHelloCheck = true,
      tcpKeepAlive = true,
      tcpSmartAccept = true,
      tcpSmartConnect = true,
      tcpLog = true
    )

    val filters = HaProxyFilter(
      name = "ie",
      condition = "hdr_sub(user-agent) MSIE",
      destination = "test_be_1_b",
      negate = false
    ) :: Nil

    val servers1 = ProxyServer(
      name = "server1",
      unixSock = "/tmp/vamp_test_be_1_a.sock",
      weight = 100
    ) :: Nil

    val servers2 = HaProxyServer(
      name = "test_be1_a_2",
      host = "192.168.59.103",
      port = 8082,
      weight = 100,
      maxConn = 1000,
      checkInterval = Option(10)
    ) :: Nil

    val backends = Backend(
      name = "name1",
      mode = Mode.http,
      proxyServers = servers1,
      servers = Nil,
      sticky = false,
      options = options
    ) :: Backend(
        name = "name2",
        mode = Mode.http,
        proxyServers = Nil,
        servers = servers2,
        sticky = false,
        options = options
      ) :: Nil

    val frontends = Frontend(
      name = "name",
      bindIp = Some("0.0.0.0"),
      bindPort = Option(8080),
      mode = Mode.http,
      unixSock = Option("/tmp/vamp_test_be_1_a.sock"),
      sockProtocol = Option("accept-proxy"),
      options = options,
      filters = filters,
      defaultBackend = backends.head
    ) :: Nil

    compare(HaProxyConfigurationTemplate(HaProxy(frontends, backends)).toString(), "configuration_1.txt")
  }

  it should "serialize single service http route to HAProxy configuration" in {

    val actual = convert(Gateway(
      name = "vamp/sava/port/-",
      port = Port(33000),
      sticky = None,
      routes = DeployedRoute(
        name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
        path = GatewayPath("vamp/sava/sava:1.0.0/port"),
        weight = Option(100),
        filters = Nil,
        targets = DeployedRouteTarget(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          host = "192.168.99.100",
          port = 32768
        ) :: Nil
      ) :: Nil))

    val backend1 = Backend(
      name = "vamp/sava/port/-",
      mode = Mode.http,
      proxyServers = ProxyServer(
        name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
        unixSock = "/opt/vamp/bd21e7864b6d08fcacdbd1b42b588d31a7cfa3e7.sock",
        weight = 100
      ) :: Nil,
      servers = Nil,
      sticky = false,
      options = Options()
    )

    val frontend1 = Frontend(
      name = "vamp/sava/port/-",
      bindIp = Option("0.0.0.0"),
      bindPort = Option(33000),
      mode = Mode.http,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = Nil,
      defaultBackend = backend1)

    val backend2 = Backend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
      mode = Mode.http,
      proxyServers = Nil,
      servers = HaProxyServer(
        name = "64435a223bddf1fa589135baa5e228090279c032",
        host = "192.168.99.100",
        port = 32768,
        weight = 100) :: Nil,
      sticky = false,
      options = Options()
    )

    val frontend2 = Frontend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/bd21e7864b6d08fcacdbd1b42b588d31a7cfa3e7.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend2)

    actual match {
      case HaProxy(List(f1, f2), List(b1, b2)) ⇒
        f1 shouldBe frontend1
        f2 shouldBe frontend2
        b1 shouldBe backend1
        b2 shouldBe backend2

      case _ ⇒ fail("can't match expected")
    }

    compare(HaProxyConfigurationTemplate(HaProxy(actual.frontends, actual.backends)).toString(), "configuration_2.txt")
  }

  it should "serialize single service tcp route to HAProxy configuration" in {
    val actual = convert(Gateway(
      name = "vamp/sava/port/-",
      port = Port("33000/tcp"),
      sticky = None,
      routes = DeployedRoute(
        name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
        path = GatewayPath("vamp/sava/sava:1.0.0/port"),
        weight = Option(100),
        filters = Nil,
        targets = DeployedRouteTarget(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          host = "192.168.99.100",
          port = 32768
        ) :: Nil
      ) :: Nil))

    val backend1 = Backend(
      name = "vamp/sava/port/-",
      mode = Mode.tcp,
      proxyServers = ProxyServer(
        name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
        unixSock = "/opt/vamp/bd21e7864b6d08fcacdbd1b42b588d31a7cfa3e7.sock",
        weight = 100
      ) :: Nil,
      servers = Nil,
      sticky = false,
      options = Options()
    )

    val frontend1 = Frontend(
      name = "vamp/sava/port/-",
      bindIp = Option("0.0.0.0"),
      bindPort = Option(33000),
      mode = Mode.tcp,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = Nil,
      defaultBackend = backend1)

    val backend2 = Backend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
      mode = Mode.tcp,
      proxyServers = Nil,
      servers = HaProxyServer(
        name = "64435a223bddf1fa589135baa5e228090279c032",
        host = "192.168.99.100",
        port = 32768,
        weight = 100) :: Nil,
      sticky = false,
      options = Options()
    )

    val frontend2 = Frontend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
      bindIp = None,
      bindPort = None,
      mode = Mode.tcp,
      unixSock = Option("/opt/vamp/bd21e7864b6d08fcacdbd1b42b588d31a7cfa3e7.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend2)

    actual match {
      case HaProxy(List(f1, f2), List(b1, b2)) ⇒
        f1 shouldBe frontend1
        f2 shouldBe frontend2
        b1 shouldBe backend1
        b2 shouldBe backend2

      case _ ⇒ fail("can't match expected")
    }

    compare(HaProxyConfigurationTemplate(HaProxy(actual.frontends, actual.backends)).toString(), "configuration_3.txt")
  }

  it should "serialize single service route with single endpoint to HAProxy configuration" in {
    val actual = convert(List(
      Gateway(
        name = "vamp/sava/port/-",
        port = Port("33002"),
        sticky = None,
        routes = DeployedRoute(
          name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
          path = GatewayPath("vamp/sava/sava:1.0.0/port"),
          weight = Option(100),
          filters = Nil,
          targets = DeployedRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 32770
          ) :: Nil)
          :: Nil
      ),
      Gateway(
        name = "vamp/port/-/-",
        port = Port("9050/tcp"),
        sticky = None,
        routes = DeployedRoute(
          name = "vamp/sava/port/-",
          path = GatewayPath("vamp/sava/port/-"),
          weight = Option(100),
          filters = Nil,
          targets = DeployedRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 33002
          ) :: Nil)
          :: Nil
      )
    ))

    val backend1 = Backend(
      name = "vamp/sava/port/-",
      mode = Mode.http,
      proxyServers = ProxyServer(
        name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
        unixSock = "/opt/vamp/bd21e7864b6d08fcacdbd1b42b588d31a7cfa3e7.sock",
        weight = 100
      ) :: Nil,
      servers = Nil,
      sticky = false,
      options = Options())

    val frontend1 = Frontend(
      name = "vamp/sava/port/-",
      bindIp = Option("0.0.0.0"),
      bindPort = Option(33002),
      mode = Mode.http,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = Nil,
      defaultBackend = backend1)

    val backend2 = Backend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
      mode = Mode.http,
      proxyServers = Nil,
      servers = HaProxyServer(
        name = "64435a223bddf1fa589135baa5e228090279c032",
        host = "192.168.99.100",
        port = 32770,
        weight = 100) :: Nil,
      sticky = false,
      options = Options())

    val frontend2 = Frontend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/bd21e7864b6d08fcacdbd1b42b588d31a7cfa3e7.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend2)

    val backend3 = Backend(
      name = "vamp/port/-/-",
      mode = Mode.tcp,
      proxyServers = ProxyServer(
        name = "vamp/port/-/-/vamp/sava/port/-",
        unixSock = "/opt/vamp/d4fb0bf347af0c6777d7ddb488fa13ac4360e338.sock",
        weight = 100
      ) :: Nil,
      servers = Nil,
      sticky = false,
      options = Options())

    val frontend3 = Frontend(
      name = "vamp/port/-/-",
      bindIp = Option("0.0.0.0"),
      bindPort = Option(9050),
      mode = Mode.tcp,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = Nil,
      defaultBackend = backend3)

    val backend4 = Backend(
      name = "vamp/port/-/-/vamp/sava/port/-",
      mode = Mode.tcp,
      proxyServers = Nil,
      servers = HaProxyServer(
        name = "64435a223bddf1fa589135baa5e228090279c032",
        host = "192.168.99.100",
        port = 33002,
        weight = 100) :: Nil,
      sticky = false,
      options = Options())

    val frontend4 = Frontend(
      name = "vamp/port/-/-/vamp/sava/port/-",
      bindIp = None,
      bindPort = None,
      mode = Mode.tcp,
      unixSock = Option("/opt/vamp/d4fb0bf347af0c6777d7ddb488fa13ac4360e338.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend4)

    actual match {
      case HaProxy(List(f1, f2, f3, f4), List(b1, b2, b3, b4)) ⇒
        f1 shouldBe frontend1
        f2 shouldBe frontend2
        f3 shouldBe frontend3
        f4 shouldBe frontend4
        b1 shouldBe backend1
        b2 shouldBe backend2
        b3 shouldBe backend3
        b4 shouldBe backend4

      case _ ⇒ fail("can't match expected")
    }

    compare(HaProxyConfigurationTemplate(HaProxy(actual.frontends, actual.backends)).toString(), "configuration_4.txt")
  }

  it should "serialize A/B services to HAProxy configuration" in {

    val actual = convert(List(
      Gateway(
        name = "vamp/sava/port/-",
        port = Port(33001),
        sticky = None,
        routes = List(
          DeployedRoute(
            name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
            path = GatewayPath("vamp/sava/sava:1.0.0/port"),
            weight = Option(90),
            filters = Nil,
            targets = List(
              DeployedRouteTarget(
                name = "64435a223bddf1fa589135baa5e228090279c032",
                host = "192.168.99.100",
                port = 32772
              ), DeployedRouteTarget(
                name = "52c84bbf28dcc96bd4c4167eeeb7ff0a69bfb6eb",
                host = "192.168.99.100",
                port = 32772
              ), DeployedRouteTarget(
                name = "5ccec1ae37f9c8f9e8eb1267bc176155541ceeb7",
                host = "192.168.99.100",
                port = 32772
              ))),
          DeployedRoute(
            name = "vamp/sava/port/-/vamp/sava/sava:1.1.0/port",
            path = GatewayPath("vamp/sava/sava:1.1.0/port"),
            weight = Option(10),
            filters = Nil,
            targets = List(
              DeployedRouteTarget(
                name = "9019c00f1f7f641c4efc7a02c6f44e9f90d7750",
                host = "192.168.99.100",
                port = 32773
              ), DeployedRouteTarget(
                name = "49594c26c89754450bd4f562946a69070a4aa887",
                host = "192.168.99.100",
                port = 32773
              )))
        )
      ),
      Gateway(
        name = "vamp/port/-/-",
        port = Port("9050/http"),
        sticky = None,
        routes = DeployedRoute(
          name = "vamp/sava/port/-",
          path = GatewayPath("vamp/sava/port/-"),
          weight = Option(100),
          filters = Nil,
          targets = DeployedRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 33002
          ) :: Nil)
          :: Nil
      )
    ))

    val backend1 = Backend(
      name = "vamp/sava/port/-",
      mode = Mode.http,
      proxyServers = List(
        ProxyServer(
          name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
          unixSock = "/opt/vamp/bd21e7864b6d08fcacdbd1b42b588d31a7cfa3e7.sock",
          weight = 90
        ),
        ProxyServer(
          name = "vamp/sava/port/-/vamp/sava/sava:1.1.0/port",
          unixSock = "/opt/vamp/2b564b6015289092cc65330cd11d18ca8c8c1318.sock",
          weight = 10
        )),
      servers = Nil,
      sticky = false,
      options = Options())

    val frontend1 = Frontend(
      name = "vamp/sava/port/-",
      bindIp = Option("0.0.0.0"),
      bindPort = Option(33001),
      mode = Mode.http,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = Nil,
      defaultBackend = backend1)

    val backend2 = Backend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
      mode = Mode.http,
      proxyServers = Nil,
      servers = List(
        HaProxyServer(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          host = "192.168.99.100",
          port = 32772,
          weight = 100),
        HaProxyServer(
          name = "52c84bbf28dcc96bd4c4167eeeb7ff0a69bfb6eb",
          host = "192.168.99.100",
          port = 32772,
          weight = 100),
        HaProxyServer(
          name = "5ccec1ae37f9c8f9e8eb1267bc176155541ceeb7",
          host = "192.168.99.100",
          port = 32772,
          weight = 100)),
      sticky = false,
      options = Options())

    val frontend2 = Frontend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/bd21e7864b6d08fcacdbd1b42b588d31a7cfa3e7.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend2)

    val backend3 = Backend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.1.0/port",
      mode = Mode.http,
      proxyServers = Nil,
      servers = List(
        HaProxyServer(
          name = "9019c00f1f7f641c4efc7a02c6f44e9f90d7750",
          host = "192.168.99.100",
          port = 32773,
          weight = 100),
        HaProxyServer(
          name = "49594c26c89754450bd4f562946a69070a4aa887",
          host = "192.168.99.100",
          port = 32773,
          weight = 100)),
      sticky = false,
      options = Options())

    val frontend3 = Frontend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.1.0/port",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/2b564b6015289092cc65330cd11d18ca8c8c1318.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend3)

    val backend4 = Backend(
      name = "vamp/port/-/-",
      mode = Mode.http,
      proxyServers = ProxyServer(
        name = "vamp/port/-/-/vamp/sava/port/-",
        unixSock = "/opt/vamp/d4fb0bf347af0c6777d7ddb488fa13ac4360e338.sock",
        weight = 100
      ) :: Nil,
      servers = Nil,
      sticky = false,
      options = Options())

    val frontend4 = Frontend(
      name = "vamp/port/-/-",
      bindIp = Option("0.0.0.0"),
      bindPort = Option(9050),
      mode = Mode.http,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = Nil,
      defaultBackend = backend4)

    val backend5 = Backend(
      name = "vamp/port/-/-/vamp/sava/port/-",
      mode = Mode.http,
      proxyServers = Nil,
      servers = HaProxyServer(
        name = "64435a223bddf1fa589135baa5e228090279c032",
        host = "192.168.99.100",
        port = 33002,
        weight = 100) :: Nil,
      sticky = false,
      options = Options())

    val frontend5 = Frontend(
      name = "vamp/port/-/-/vamp/sava/port/-",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/d4fb0bf347af0c6777d7ddb488fa13ac4360e338.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend5)

    actual match {
      case HaProxy(List(f1, f2, f3, f4, f5), List(b1, b2, b3, b4, b5)) ⇒
        f1 shouldBe frontend1
        f2 shouldBe frontend2
        f3 shouldBe frontend3
        f4 shouldBe frontend4
        f5 shouldBe frontend5
        b1 shouldBe backend1
        b2 shouldBe backend2
        b3 shouldBe backend3
        b4 shouldBe backend4
        b5 shouldBe backend5

      case _ ⇒ fail("can't match expected")
    }

    compare(HaProxyConfigurationTemplate(HaProxy(actual.frontends, actual.backends)).toString(), "configuration_5.txt")
  }

  it should "serialize services with dependency to HAProxy configuration" in {
    val actual = convert(List(
      Gateway(
        name = "vamp/backend/port",
        port = Port(33003),
        sticky = None,
        routes = DeployedRoute(
          name = "vamp/sava/sava-backend:1.3.0/port",
          path = GatewayPath("vamp/sava/sava-backend:1.3.0/port"),
          weight = Option(100),
          filters = Nil,
          targets = DeployedRouteTarget(
            name = "57c4e3d2cbb8f0db907f5e16ceed9a4241d7e117",
            host = "192.168.99.100",
            port = 32770
          ) :: Nil)
          :: Nil
      ),
      Gateway(
        name = "vamp/sava/port/-",
        port = Port("33002"),
        sticky = None,
        routes = DeployedRoute(
          name = "vamp/sava/sava-frontend:1.3.0/port",
          path = GatewayPath("vamp/sava/sava-frontend:1.3.0/port"),
          weight = Option(100),
          filters = Nil,
          targets = DeployedRouteTarget(
            name = "f1638245acf2ebe6db56984a85b48f6db8c74607",
            host = "192.168.99.100",
            port = 32771
          ) :: Nil)
          :: Nil
      ),
      Gateway(
        name = "vamp/port/-/-",
        port = Port("9050/http"),
        sticky = None,
        routes = DeployedRoute(
          name = "vamp/sava/port/-",
          path = GatewayPath("vamp/sava/port/-"),
          weight = Option(100),
          filters = Nil,
          targets = DeployedRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 33002
          ) :: Nil)
          :: Nil
      )
    ))

    val backend1 = Backend(
      name = "vamp/backend/port/-",
      mode = Mode.http,
      proxyServers = List(
        ProxyServer(
          name = "vamp/backend/port/-/vamp/sava/sava-backend:1.3.0/port",
          unixSock = "/opt/vamp/a48dbb53522c4cb49684c0db8280a881328c0b1.sock",
          weight = 100
        )),
      servers = Nil,
      sticky = false,
      options = Options())

    val frontend1 = Frontend(
      name = "vamp/backend/port/-",
      bindIp = Option("0.0.0.0"),
      bindPort = Option(33003),
      mode = Mode.http,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = Nil,
      defaultBackend = backend1)

    val backend2 = Backend(
      name = "vamp/backend/port/-/vamp/sava/sava-backend:1.3.0/port",
      mode = Mode.http,
      proxyServers = Nil,
      servers = List(
        HaProxyServer(
          name = "57c4e3d2cbb8f0db907f5e16ceed9a4241d7e117",
          host = "192.168.99.100",
          port = 32770,
          weight = 100)),
      sticky = false,
      options = Options())

    val frontend2 = Frontend(
      name = "vamp/backend/port/-/vamp/sava/sava-backend:1.3.0/port",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/a48dbb53522c4cb49684c0db8280a881328c0b1.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend2)

    val backend3 = Backend(
      name = "vamp/sava/port/-",
      mode = Mode.http,
      proxyServers = List(
        ProxyServer(
          name = "vamp/sava/port/-/vamp/sava/sava-frontend:1.3.0/port",
          unixSock = "/opt/vamp/ff6c1e43521976fe84a2f61d5818bc948ae8ca85.sock",
          weight = 100
        )),
      servers = Nil,
      sticky = false,
      options = Options())

    val frontend3 = Frontend(
      name = "vamp/sava/port/-",
      bindIp = Option("0.0.0.0"),
      bindPort = Option(33002),
      mode = Mode.http,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = Nil,
      defaultBackend = backend3)

    val backend4 = Backend(
      name = "vamp/sava/port/-/vamp/sava/sava-frontend:1.3.0/port",
      mode = Mode.http,
      proxyServers = Nil,
      servers = List(
        HaProxyServer(
          name = "f1638245acf2ebe6db56984a85b48f6db8c74607",
          host = "192.168.99.100",
          port = 32771,
          weight = 100)),
      sticky = false,
      options = Options())

    val frontend4 = Frontend(
      name = "vamp/sava/port/-/vamp/sava/sava-frontend:1.3.0/port",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/ff6c1e43521976fe84a2f61d5818bc948ae8ca85.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend4)

    val backend5 = Backend(
      name = "vamp/port/-/-",
      mode = Mode.http,
      proxyServers = ProxyServer(
        name = "vamp/port/-/-/vamp/sava/port/-",
        unixSock = "/opt/vamp/d4fb0bf347af0c6777d7ddb488fa13ac4360e338.sock",
        weight = 100
      ) :: Nil,
      servers = Nil,
      sticky = false,
      options = Options())

    val frontend5 = Frontend(
      name = "vamp/port/-/-",
      bindIp = Option("0.0.0.0"),
      bindPort = Option(9050),
      mode = Mode.http,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = Nil,
      defaultBackend = backend5)

    val backend6 = Backend(
      name = "vamp/port/-/-/vamp/sava/port/-",
      mode = Mode.http,
      proxyServers = Nil,
      servers = HaProxyServer(
        name = "64435a223bddf1fa589135baa5e228090279c032",
        host = "192.168.99.100",
        port = 33002,
        weight = 100) :: Nil,
      sticky = false,
      options = Options())

    val frontend6 = Frontend(
      name = "vamp/port/-/-/vamp/sava/port/-",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/d4fb0bf347af0c6777d7ddb488fa13ac4360e338.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend6)

    actual match {
      case HaProxy(List(f1, f2, f3, f4, f5, f6), List(b1, b2, b3, b4, b5, b6)) ⇒
        f1 shouldBe frontend1
        f2 shouldBe frontend2
        f3 shouldBe frontend3
        f4 shouldBe frontend4
        f5 shouldBe frontend5
        f6 shouldBe frontend6
        b1 shouldBe backend1
        b2 shouldBe backend2
        b3 shouldBe backend3
        b4 shouldBe backend4
        b5 shouldBe backend5
        b6 shouldBe backend6

      case _ ⇒ fail("can't match expected")
    }

    compare(HaProxyConfigurationTemplate(HaProxy(actual.frontends, actual.backends)).toString(), "configuration_6.txt")
  }

  it should "convert filters" in {
    implicit val route = DefaultRoute("sava", GatewayPath("sava"), None, Nil)

    List(
      ("hdr_sub(user-agent) Android", "hdr_sub(user-agent) Android", false),
      ("user-agent=Android", "hdr_sub(user-agent) Android", false),
      ("user-agent!=Android", "hdr_sub(user-agent) Android", true),
      ("User-Agent=Android", "hdr_sub(user-agent) Android", false),
      ("user-agent = Android", "hdr_sub(user-agent) Android", false),
      ("user-agent  =  Android", "user-agent  =  Android", false),
      ("user.agent = Ios", "hdr_sub(user-agent) Ios", false),
      ("host = www.google.com", "hdr_str(host) www.google.com", false),
      ("host != www.google.com", "hdr_str(host) www.google.com", true),
      ("cookie MYCUSTOMER contains Value=good", "cook_sub(MYCUSTOMER) Value=good", false),
      ("has cookie JSESSIONID", "cook(JSESSIONID) -m found", false),
      ("misses cookie JSESSIONID", "cook_cnt(JSESSIONID) eq 0", false),
      ("has header X-SPECIAL", "hdr_cnt(X-SPECIAL) gt 0", false),
      ("misses header X-SPECIAL", "hdr_cnt(X-SPECIAL) eq 0", false)
    ) foreach { input ⇒
        filter(route, DefaultFilter("", input._1))(Gateway("", Port(0), None, Nil)) match {
          case HaProxyFilter(_, condition, _, negate) ⇒
            input._2 shouldBe condition
            input._3 shouldBe negate
        }
      }
  }

  it should "serialize service with filters to HAProxy configuration" in {
    val actual = convert(
      Gateway(
        name = "vamp/sava/port/-",
        port = Port(33000),
        sticky = None,
        routes = DeployedRoute(
          name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
          path = GatewayPath("vamp/sava/sava:1.0.0/port"),
          weight = Option(100),
          filters = List(
            DefaultFilter(
              name = "",
              condition = "user-agent != ie"
            ), DefaultFilter(
              name = "",
              condition = "user-agent = chrome"
            ), DefaultFilter(
              name = "",
              condition = "cookie group contains admin"
            ), DefaultFilter(
              name = "",
              condition = "has header x-allow"
            )
          ),
          targets = DeployedRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 32776
          ) :: Nil
        ) :: Nil)
    )

    val backend1 = Backend(
      name = "vamp/sava/port/-",
      mode = Mode.http,
      proxyServers = List(
        ProxyServer(
          name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
          unixSock = "/opt/vamp/bd21e7864b6d08fcacdbd1b42b588d31a7cfa3e7.sock",
          weight = 100
        )),
      servers = Nil,
      sticky = false,
      options = Options())

    val frontend1 = Frontend(
      name = "vamp/sava/port/-",
      bindIp = Option("0.0.0.0"),
      bindPort = Option(33000),
      mode = Mode.http,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = List(
        HaProxyFilter(
          name = "624dc0f1a5754e94",
          condition = "hdr_sub(user-agent) ie",
          destination = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
          negate = true
        ),
        HaProxyFilter(
          name = "3cb39f8aae4d99da",
          condition = "hdr_sub(user-agent) chrome",
          destination = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port"
        ),
        HaProxyFilter(
          name = "445a3abaca79c140",
          condition = "cook_sub(group) admin",
          destination = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port"
        ),
        HaProxyFilter(
          name = "feb4c187ccc342ce",
          condition = "hdr_cnt(x-allow) gt 0",
          destination = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port"
        )
      ),
      defaultBackend = backend1)

    val backend2 = Backend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
      mode = Mode.http,
      proxyServers = Nil,
      servers = List(
        HaProxyServer(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          host = "192.168.99.100",
          port = 32776,
          weight = 100)),
      sticky = false,
      options = Options())

    val frontend2 = Frontend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/bd21e7864b6d08fcacdbd1b42b588d31a7cfa3e7.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend2)

    actual match {
      case HaProxy(List(f1, f2), List(b1, b2)) ⇒
        f1 shouldBe frontend1
        f2 shouldBe frontend2
        b1 shouldBe backend1
        b2 shouldBe backend2

      case _ ⇒ fail("can't match expected")
    }

    compare(HaProxyConfigurationTemplate(HaProxy(actual.frontends, actual.backends)).toString(), "configuration_7.txt")
  }

  it should "serialize A/B services to HAProxy configuration - sticky service" in {
    val actual = convert(List(
      Gateway(
        name = "vamp/sava/port/-",
        port = Port(33001),
        sticky = Some(Gateway.Sticky.Service),
        routes = List(
          DeployedRoute(
            name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
            path = GatewayPath("vamp/sava/sava:1.0.0/port"),
            weight = Option(90),
            filters = Nil,
            targets = List(
              DeployedRouteTarget(
                name = "64435a223bddf1fa589135baa5e228090279c032",
                host = "192.168.99.100",
                port = 32772
              ), DeployedRouteTarget(
                name = "52c84bbf28dcc96bd4c4167eeeb7ff0a69bfb6eb",
                host = "192.168.99.100",
                port = 32772
              ), DeployedRouteTarget(
                name = "5ccec1ae37f9c8f9e8eb1267bc176155541ceeb7",
                host = "192.168.99.100",
                port = 32772
              ))),
          DeployedRoute(
            name = "vamp/sava/port/-/vamp/sava/sava:1.1.0/port",
            path = GatewayPath("vamp/sava/sava:1.1.0/port"),
            weight = Option(10),
            filters = Nil,
            targets = List(
              DeployedRouteTarget(
                name = "9019c00f1f7f641c4efc7a02c6f44e9f90d7750",
                host = "192.168.99.100",
                port = 32773
              ), DeployedRouteTarget(
                name = "49594c26c89754450bd4f562946a69070a4aa887",
                host = "192.168.99.100",
                port = 32773
              )))
        )
      ),
      Gateway(
        name = "vamp/port/-/-",
        port = Port("9050/http"),
        sticky = None,
        routes = DeployedRoute(
          name = "vamp/sava/port/-",
          path = GatewayPath("vamp/sava/port/-"),
          weight = Option(100),
          filters = Nil,
          targets = DeployedRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 33002
          ) :: Nil)
          :: Nil
      )
    ))

    val backend1 = Backend(
      name = "vamp/sava/port/-",
      mode = Mode.http,
      proxyServers = List(
        ProxyServer(
          name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
          unixSock = "/opt/vamp/bd21e7864b6d08fcacdbd1b42b588d31a7cfa3e7.sock",
          weight = 90
        ),
        ProxyServer(
          name = "vamp/sava/port/-/vamp/sava/sava:1.1.0/port",
          unixSock = "/opt/vamp/2b564b6015289092cc65330cd11d18ca8c8c1318.sock",
          weight = 10
        )),
      servers = Nil,
      sticky = true,
      options = Options())

    val frontend1 = Frontend(
      name = "vamp/sava/port/-",
      bindIp = Option("0.0.0.0"),
      bindPort = Option(33001),
      mode = Mode.http,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = Nil,
      defaultBackend = backend1)

    val backend2 = Backend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
      mode = Mode.http,
      proxyServers = Nil,
      servers = List(
        HaProxyServer(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          host = "192.168.99.100",
          port = 32772,
          weight = 100),
        HaProxyServer(
          name = "52c84bbf28dcc96bd4c4167eeeb7ff0a69bfb6eb",
          host = "192.168.99.100",
          port = 32772,
          weight = 100),
        HaProxyServer(
          name = "5ccec1ae37f9c8f9e8eb1267bc176155541ceeb7",
          host = "192.168.99.100",
          port = 32772,
          weight = 100)),
      sticky = false,
      options = Options())

    val frontend2 = Frontend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/bd21e7864b6d08fcacdbd1b42b588d31a7cfa3e7.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend2)

    val backend3 = Backend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.1.0/port",
      mode = Mode.http,
      proxyServers = Nil,
      servers = List(
        HaProxyServer(
          name = "9019c00f1f7f641c4efc7a02c6f44e9f90d7750",
          host = "192.168.99.100",
          port = 32773,
          weight = 100),
        HaProxyServer(
          name = "49594c26c89754450bd4f562946a69070a4aa887",
          host = "192.168.99.100",
          port = 32773,
          weight = 100)),
      sticky = false,
      options = Options())

    val frontend3 = Frontend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.1.0/port",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/2b564b6015289092cc65330cd11d18ca8c8c1318.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend3)

    val backend4 = Backend(
      name = "vamp/port/-/-",
      mode = Mode.http,
      proxyServers = ProxyServer(
        name = "vamp/port/-/-/vamp/sava/port/-",
        unixSock = "/opt/vamp/d4fb0bf347af0c6777d7ddb488fa13ac4360e338.sock",
        weight = 100
      ) :: Nil,
      servers = Nil,
      sticky = false,
      options = Options())

    val frontend4 = Frontend(
      name = "vamp/port/-/-",
      bindIp = Option("0.0.0.0"),
      bindPort = Option(9050),
      mode = Mode.http,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = Nil,
      defaultBackend = backend4)

    val backend5 = Backend(
      name = "vamp/port/-/-/vamp/sava/port/-",
      mode = Mode.http,
      proxyServers = Nil,
      servers = HaProxyServer(
        name = "64435a223bddf1fa589135baa5e228090279c032",
        host = "192.168.99.100",
        port = 33002,
        weight = 100) :: Nil,
      sticky = false,
      options = Options())

    val frontend5 = Frontend(
      name = "vamp/port/-/-/vamp/sava/port/-",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/d4fb0bf347af0c6777d7ddb488fa13ac4360e338.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend5)

    actual match {
      case HaProxy(List(f1, f2, f3, f4, f5), List(b1, b2, b3, b4, b5)) ⇒
        f1 shouldBe frontend1
        f2 shouldBe frontend2
        f3 shouldBe frontend3
        f4 shouldBe frontend4
        f5 shouldBe frontend5
        b1 shouldBe backend1
        b2 shouldBe backend2
        b3 shouldBe backend3
        b4 shouldBe backend4
        b5 shouldBe backend5

      case _ ⇒ fail("can't match expected")
    }

    compare(HaProxyConfigurationTemplate(HaProxy(actual.frontends, actual.backends)).toString(), "configuration_8.txt")
  }

  it should "serialize A/B services to HAProxy configuration - sticky instance" in {
    val actual = convert(List(
      Gateway(
        name = "vamp/sava/port/-",
        port = Port(33001),
        sticky = Some(Gateway.Sticky.Instance),
        routes = List(
          DeployedRoute(
            name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
            path = GatewayPath("vamp/sava/sava:1.0.0/port"),
            weight = Option(90),
            filters = Nil,
            targets = List(
              DeployedRouteTarget(
                name = "64435a223bddf1fa589135baa5e228090279c032",
                host = "192.168.99.100",
                port = 32772
              ), DeployedRouteTarget(
                name = "52c84bbf28dcc96bd4c4167eeeb7ff0a69bfb6eb",
                host = "192.168.99.100",
                port = 32772
              ), DeployedRouteTarget(
                name = "5ccec1ae37f9c8f9e8eb1267bc176155541ceeb7",
                host = "192.168.99.100",
                port = 32772
              ))),
          DeployedRoute(
            name = "vamp/sava/port/-/vamp/sava/sava:1.1.0/port",
            path = GatewayPath("vamp/sava/sava:1.1.0/port"),
            weight = Option(10),
            filters = Nil,
            targets = List(
              DeployedRouteTarget(
                name = "9019c00f1f7f641c4efc7a02c6f44e9f90d7750",
                host = "192.168.99.100",
                port = 32773
              ), DeployedRouteTarget(
                name = "49594c26c89754450bd4f562946a69070a4aa887",
                host = "192.168.99.100",
                port = 32773
              )))
        )
      ),
      Gateway(
        name = "vamp/port/-/-",
        port = Port("9050/http"),
        sticky = None,
        routes = DeployedRoute(
          name = "vamp/sava/port/-",
          path = GatewayPath("vamp/sava/port/-"),
          weight = Option(100),
          filters = Nil,
          targets = DeployedRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 33002
          ) :: Nil)
          :: Nil
      )
    ))

    val backend1 = Backend(
      name = "vamp/sava/port/-",
      mode = Mode.http,
      proxyServers = List(
        ProxyServer(
          name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
          unixSock = "/opt/vamp/bd21e7864b6d08fcacdbd1b42b588d31a7cfa3e7.sock",
          weight = 90
        ),
        ProxyServer(
          name = "vamp/sava/port/-/vamp/sava/sava:1.1.0/port",
          unixSock = "/opt/vamp/2b564b6015289092cc65330cd11d18ca8c8c1318.sock",
          weight = 10
        )),
      servers = Nil,
      sticky = true,
      options = Options())

    val frontend1 = Frontend(
      name = "vamp/sava/port/-",
      bindIp = Option("0.0.0.0"),
      bindPort = Option(33001),
      mode = Mode.http,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = Nil,
      defaultBackend = backend1)

    val backend2 = Backend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
      mode = Mode.http,
      proxyServers = Nil,
      servers = List(
        HaProxyServer(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          host = "192.168.99.100",
          port = 32772,
          weight = 100),
        HaProxyServer(
          name = "52c84bbf28dcc96bd4c4167eeeb7ff0a69bfb6eb",
          host = "192.168.99.100",
          port = 32772,
          weight = 100),
        HaProxyServer(
          name = "5ccec1ae37f9c8f9e8eb1267bc176155541ceeb7",
          host = "192.168.99.100",
          port = 32772,
          weight = 100)),
      sticky = true,
      options = Options())

    val frontend2 = Frontend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.0.0/port",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/bd21e7864b6d08fcacdbd1b42b588d31a7cfa3e7.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend2)

    val backend3 = Backend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.1.0/port",
      mode = Mode.http,
      proxyServers = Nil,
      servers = List(
        HaProxyServer(
          name = "9019c00f1f7f641c4efc7a02c6f44e9f90d7750",
          host = "192.168.99.100",
          port = 32773,
          weight = 100),
        HaProxyServer(
          name = "49594c26c89754450bd4f562946a69070a4aa887",
          host = "192.168.99.100",
          port = 32773,
          weight = 100)),
      sticky = true,
      options = Options())

    val frontend3 = Frontend(
      name = "vamp/sava/port/-/vamp/sava/sava:1.1.0/port",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/2b564b6015289092cc65330cd11d18ca8c8c1318.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend3)

    val backend4 = Backend(
      name = "vamp/port/-/-",
      mode = Mode.http,
      proxyServers = ProxyServer(
        name = "vamp/port/-/-/vamp/sava/port/-",
        unixSock = "/opt/vamp/d4fb0bf347af0c6777d7ddb488fa13ac4360e338.sock",
        weight = 100
      ) :: Nil,
      servers = Nil,
      sticky = false,
      options = Options())

    val frontend4 = Frontend(
      name = "vamp/port/-/-",
      bindIp = Option("0.0.0.0"),
      bindPort = Option(9050),
      mode = Mode.http,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = Nil,
      defaultBackend = backend4)

    val backend5 = Backend(
      name = "vamp/port/-/-/vamp/sava/port/-",
      mode = Mode.http,
      proxyServers = Nil,
      servers = HaProxyServer(
        name = "64435a223bddf1fa589135baa5e228090279c032",
        host = "192.168.99.100",
        port = 33002,
        weight = 100) :: Nil,
      sticky = false,
      options = Options())

    val frontend5 = Frontend(
      name = "vamp/port/-/-/vamp/sava/port/-",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/d4fb0bf347af0c6777d7ddb488fa13ac4360e338.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend5)

    actual match {
      case HaProxy(List(f1, f2, f3, f4, f5), List(b1, b2, b3, b4, b5)) ⇒
        f1 shouldBe frontend1
        f2 shouldBe frontend2
        f3 shouldBe frontend3
        f4 shouldBe frontend4
        f5 shouldBe frontend5
        b1 shouldBe backend1
        b2 shouldBe backend2
        b3 shouldBe backend3
        b4 shouldBe backend4
        b5 shouldBe backend5

      case _ ⇒ fail("can't match expected")
    }

    compare(HaProxyConfigurationTemplate(HaProxy(actual.frontends, actual.backends)).toString(), "configuration_9.txt")
  }

  it should "serialize A/B testing on deployments" in {
    val actual = convert(List(
      Gateway(
        name = "vamp:1.x/sava/port",
        port = Port(33001),
        sticky = None,
        routes = DeployedRoute(
          name = "vamp:1.x/sava/sava:1.0.0/port",
          path = GatewayPath("vamp:1.x/sava/sava:1.0.0/port"),
          weight = Option(100),
          filters = Nil,
          targets = DeployedRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 32770
          ) :: Nil)
          :: Nil
      ),
      Gateway(
        name = "vamp:2.x/sava/port",
        port = Port(33001),
        sticky = None,
        routes = DeployedRoute(
          name = "vamp:2.x/sava/sava:2.0.0/port",
          path = GatewayPath("vamp:2.x/sava/sava:2.0.0/port"),
          weight = Option(100),
          filters = Nil,
          targets = DeployedRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.101",
            port = 32771
          ) :: Nil)
          :: Nil
      ),
      Gateway(
        name = "vamp",
        port = Port("9050/http"),
        sticky = None,
        routes = List(
          DeployedRoute(
            name = "vamp:1.x/sava/port",
            path = GatewayPath("vamp:1.x/sava/port"),
            weight = Option(90),
            filters = Nil,
            targets = List(
              DeployedRouteTarget(
                name = "64435a223bddf1fa589135baa5e228090279c032",
                host = "192.168.99.100",
                port = 32772
              ))),
          DeployedRoute(
            name = "vamp:2.x/sava/port",
            path = GatewayPath("vamp:2.x/sava/port"),
            weight = Option(10),
            filters = Nil,
            targets = List(
              DeployedRouteTarget(
                name = "9019c00f1f7f641c4efc7a02c6f44e9f90d7750",
                host = "192.168.99.100",
                port = 32773
              )))
        ))
    ))

    val backend1 = Backend(
      name = "vamp:1.x/sava/port/-",
      mode = Mode.http,
      proxyServers = List(
        ProxyServer(
          name = "vamp:1.x/sava/port/-/vamp:1.x/sava/sava:1.0.0/port",
          unixSock = "/opt/vamp/94ec2082c62403bfed22d71fe4f38229330ebd27.sock",
          weight = 100
        )),
      servers = Nil,
      sticky = false,
      options = Options())

    val frontend1 = Frontend(
      name = "vamp:1.x/sava/port/-",
      bindIp = Option("0.0.0.0"),
      bindPort = Option(33001),
      mode = Mode.http,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = Nil,
      defaultBackend = backend1)

    val backend2 = Backend(
      name = "vamp:1.x/sava/port/-/vamp:1.x/sava/sava:1.0.0/port",
      mode = Mode.http,
      proxyServers = Nil,
      servers = List(
        HaProxyServer(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          host = "192.168.99.100",
          port = 32770,
          weight = 100)),
      sticky = false,
      options = Options())

    val frontend2 = Frontend(
      name = "vamp:1.x/sava/port/-/vamp:1.x/sava/sava:1.0.0/port",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/94ec2082c62403bfed22d71fe4f38229330ebd27.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend2)

    val backend3 = Backend(
      name = "vamp:2.x/sava/port/-",
      mode = Mode.http,
      proxyServers = List(
        ProxyServer(
          name = "vamp:2.x/sava/port/-/vamp:2.x/sava/sava:2.0.0/port",
          unixSock = "/opt/vamp/bdecefb6a0fe1e1cd1093f701284e1486cba0c76.sock",
          weight = 100
        )),
      servers = Nil,
      sticky = false,
      options = Options())

    val frontend3 = Frontend(
      name = "vamp:2.x/sava/port/-",
      bindIp = Option("0.0.0.0"),
      bindPort = Option(33001),
      mode = Mode.http,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = Nil,
      defaultBackend = backend3)

    val backend4 = Backend(
      name = "vamp:2.x/sava/port/-/vamp:2.x/sava/sava:2.0.0/port",
      mode = Mode.http,
      proxyServers = Nil,
      servers = List(
        HaProxyServer(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          host = "192.168.99.101",
          port = 32771,
          weight = 100)),
      sticky = false,
      options = Options())

    val frontend4 = Frontend(
      name = "vamp:2.x/sava/port/-/vamp:2.x/sava/sava:2.0.0/port",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/bdecefb6a0fe1e1cd1093f701284e1486cba0c76.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend4)

    val backend5 = Backend(
      name = "vamp/-/-/-",
      mode = Mode.http,
      proxyServers = ProxyServer(
        name = "vamp/-/-/-/vamp:1.x/sava/port/-",
        unixSock = "/opt/vamp/225cba0a38ff23124d6db98ab6c1e9abff810769.sock",
        weight = 90
      ) :: ProxyServer(
          name = "vamp/-/-/-/vamp:2.x/sava/port/-",
          unixSock = "/opt/vamp/4254eb5ec2c4d56d5a8dfb430c324848e6569693.sock",
          weight = 10
        ) :: Nil,
      servers = Nil,
      sticky = false,
      options = Options())

    val frontend5 = Frontend(
      name = "vamp/-/-/-",
      bindIp = Option("0.0.0.0"),
      bindPort = Option(9050),
      mode = Mode.http,
      unixSock = None,
      sockProtocol = None,
      options = Options(),
      filters = Nil,
      defaultBackend = backend5)

    val backend6 = Backend(
      name = "vamp/-/-/-/vamp:1.x/sava/port/-",
      mode = Mode.http,
      proxyServers = Nil,
      servers = HaProxyServer(
        name = "64435a223bddf1fa589135baa5e228090279c032",
        host = "192.168.99.100",
        port = 32772,
        weight = 100) :: Nil,
      sticky = false,
      options = Options())

    val frontend6 = Frontend(
      name = "vamp/-/-/-/vamp:1.x/sava/port/-",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/225cba0a38ff23124d6db98ab6c1e9abff810769.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend6)

    val backend7 = Backend(
      name = "vamp/-/-/-/vamp:2.x/sava/port/-",
      mode = Mode.http,
      proxyServers = Nil,
      servers = HaProxyServer(
        name = "9019c00f1f7f641c4efc7a02c6f44e9f90d7750",
        host = "192.168.99.100",
        port = 32773,
        weight = 100) :: Nil,
      sticky = false,
      options = Options())

    val frontend7 = Frontend(
      name = "vamp/-/-/-/vamp:2.x/sava/port/-",
      bindIp = None,
      bindPort = None,
      mode = Mode.http,
      unixSock = Option("/opt/vamp/4254eb5ec2c4d56d5a8dfb430c324848e6569693.sock"),
      sockProtocol = Option("accept-proxy"),
      options = Options(),
      filters = Nil,
      defaultBackend = backend7)

    actual match {
      case HaProxy(List(f1, f2, f3, f4, f5, f6, f7), List(b1, b2, b3, b4, b5, b6, b7)) ⇒
        f1 shouldBe frontend1
        f2 shouldBe frontend2
        f3 shouldBe frontend3
        f4 shouldBe frontend4
        f5 shouldBe frontend5
        f6 shouldBe frontend6
        f7 shouldBe frontend7
        b1 shouldBe backend1
        b2 shouldBe backend2
        b3 shouldBe backend3
        b4 shouldBe backend4
        b5 shouldBe backend5
        b6 shouldBe backend6
        b7 shouldBe backend7

      case _ ⇒ fail("can't match expected")
    }

    compare(HaProxyConfigurationTemplate(HaProxy(actual.frontends, actual.backends)).toString(), "configuration_10.txt")
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
