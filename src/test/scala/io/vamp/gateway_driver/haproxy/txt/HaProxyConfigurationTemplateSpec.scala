package io.vamp.gateway_driver.haproxy.txt

import io.vamp.gateway_driver.haproxy.{ Filter ⇒ HaProxyFilter, Server ⇒ HaProxyServer, _ }
import io.vamp.gateway_driver.model.{ Filter, Gateway, Server, Service }
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{ Informer, FlatSpec, Matchers }

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

    val frontends = Frontend(
      name = "name",
      bindIp = Some("0.0.0.0"),
      bindPort = Option(8080),
      mode = Interface.Mode.http,
      unixSock = Option("/tmp/vamp_test_be_1_a.sock"),
      sockProtocol = Option("accept-proxy"),
      options = options,
      filters = filters,
      defaultBackend = "test_be_1"
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
      mode = Interface.Mode.http,
      proxyServers = servers1,
      servers = Nil,
      options = options
    ) :: Backend(
        name = "name2",
        mode = Interface.Mode.http,
        proxyServers = Nil,
        servers = servers2,
        options = options
      ) :: Nil

    compare(HaProxyConfigurationTemplate(HaProxy(frontends, backends)).toString(), "configuration_1.txt")
  }

  it should "serialize single service http route to HAProxy configuration" in {

    val actual = convert(Gateway(
      name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080",
      port = 33000,
      protocol = "http",
      filters = Nil,
      services = Service(
        name = "sava:1.0.0",
        weight = 100,
        servers = Server(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          host = "192.168.99.100",
          port = 32768) :: Nil
      ) :: Nil))

    val expected = HaProxy(List(
      Frontend(
        name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080",
        bindIp = Option("0.0.0.0"),
        bindPort = Option(33000),
        mode = Interface.Mode.http,
        unixSock = None,
        sockProtocol = None,
        options = Options(),
        filters = Nil,
        defaultBackend = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080"),
      Frontend(
        name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080::sava:1.0.0",
        bindIp = None,
        bindPort = None,
        mode = Interface.Mode.http,
        unixSock = Option("/opt/vamp/59e3034c619dd724b2e57d38218afdcd63e6ad8a.sock"),
        sockProtocol = Option("accept-proxy"),
        options = Options(),
        filters = Nil,
        defaultBackend = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080::sava:1.0.0")
    ), List(
      Backend(
        name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080",
        mode = Interface.Mode.http,
        proxyServers = ProxyServer(
          name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080::sava:1.0.0",
          unixSock = "/opt/vamp/59e3034c619dd724b2e57d38218afdcd63e6ad8a.sock",
          weight = 100
        ) :: Nil,
        servers = Nil,
        options = Options()),
      Backend(
        name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080::sava:1.0.0",
        mode = Interface.Mode.http,
        proxyServers = Nil,
        servers = HaProxyServer(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          host = "192.168.99.100",
          port = 32768,
          weight = 100) :: Nil,
        options = Options())
    ))

    actual shouldBe expected
    compare(HaProxyConfigurationTemplate(HaProxy(actual.frontends, actual.backends)).toString(), "configuration_2.txt")
  }

  it should "serialize single service tcp route to HAProxy configuration" in {
    val actual = convert(Gateway(
      name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080",
      port = 33000,
      protocol = "tcp",
      filters = Nil,
      services = Service(
        name = "sava:1.0.0",
        weight = 100,
        servers = Server(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          host = "192.168.99.100",
          port = 32768) :: Nil
      ) :: Nil))

    val expected = HaProxy(List(
      Frontend(
        name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080",
        bindIp = Option("0.0.0.0"),
        bindPort = Option(33000),
        mode = Interface.Mode.tcp,
        unixSock = None,
        sockProtocol = None,
        options = Options(),
        filters = Nil,
        defaultBackend = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080"),
      Frontend(
        name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080::sava:1.0.0",
        bindIp = None,
        bindPort = None,
        mode = Interface.Mode.tcp,
        unixSock = Option("/opt/vamp/59e3034c619dd724b2e57d38218afdcd63e6ad8a.sock"),
        sockProtocol = Option("accept-proxy"),
        options = Options(),
        filters = Nil,
        defaultBackend = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080::sava:1.0.0")
    ),
      List(
        Backend(
          name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080",
          mode = Interface.Mode.tcp,
          proxyServers = ProxyServer(
            name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080::sava:1.0.0",
            unixSock = "/opt/vamp/59e3034c619dd724b2e57d38218afdcd63e6ad8a.sock",
            weight = 100
          ) :: Nil,
          servers = Nil,
          options = Options()),
        Backend(
          name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080::sava:1.0.0",
          mode = Interface.Mode.tcp,
          proxyServers = Nil,
          servers = HaProxyServer(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 32768,
            weight = 100) :: Nil,
          options = Options())
      ))

    actual shouldBe expected
    compare(HaProxyConfigurationTemplate(HaProxy(actual.frontends, actual.backends)).toString(), "configuration_3.txt")
  }

  it should "serialize single service route with single endpoint to HAProxy configuration" in {
    val actual = convert(List(
      Gateway(
        name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_sava_8080",
        port = 33002,
        protocol = "http",
        filters = Nil,
        services = Service(
          name = "sava:1.0.0",
          weight = 100,
          servers = Server(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 32770) :: Nil
        ) :: Nil),
      Gateway(
        name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050",
        port = 9050,
        protocol = "tcp",
        filters = Nil,
        services = Service(
          name = "sava.port",
          weight = 100,
          servers = Server(
            name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050",
            host = "192.168.99.100",
            port = 33002) :: Nil
        ) :: Nil)
    ))

    val expected = HaProxy(List(
      Frontend(
        name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_sava_8080",
        bindIp = Option("0.0.0.0"),
        bindPort = Option(33002),
        mode = Interface.Mode.http,
        unixSock = None,
        sockProtocol = None,
        options = Options(),
        filters = Nil,
        defaultBackend = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_sava_8080"),
      Frontend(
        name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_sava_8080::sava:1.0.0",
        bindIp = None,
        bindPort = None,
        mode = Interface.Mode.http,
        unixSock = Option("/opt/vamp/d6e29d4976d84d757dbb6b753d6ad14370d6ca96.sock"),
        sockProtocol = Option("accept-proxy"),
        options = Options(),
        filters = Nil,
        defaultBackend = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_sava_8080::sava:1.0.0"),
      Frontend(
        name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050",
        bindIp = Option("0.0.0.0"),
        bindPort = Option(9050),
        mode = Interface.Mode.tcp,
        unixSock = None,
        sockProtocol = None,
        options = Options(),
        filters = Nil,
        defaultBackend = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050"),
      Frontend(
        name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050::sava.port",
        bindIp = None,
        bindPort = None,
        mode = Interface.Mode.tcp,
        unixSock = Option("/opt/vamp/8902264b2a19b47f814a170c357c2611ec4cd621.sock"),
        sockProtocol = Option("accept-proxy"),
        options = Options(),
        filters = Nil,
        defaultBackend = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050::sava.port")
    ),
      List(
        Backend(
          name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_sava_8080",
          mode = Interface.Mode.http,
          proxyServers = ProxyServer(
            name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_sava_8080::sava:1.0.0",
            unixSock = "/opt/vamp/d6e29d4976d84d757dbb6b753d6ad14370d6ca96.sock",
            weight = 100
          ) :: Nil,
          servers = Nil,
          options = Options()),
        Backend(
          name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_sava_8080::sava:1.0.0",
          mode = Interface.Mode.http,
          proxyServers = Nil,
          servers = HaProxyServer(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 32770,
            weight = 100) :: Nil,
          options = Options()),
        Backend(
          name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050",
          mode = Interface.Mode.tcp,
          proxyServers = ProxyServer(
            name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050::sava.port",
            unixSock = "/opt/vamp/8902264b2a19b47f814a170c357c2611ec4cd621.sock",
            weight = 100
          ) :: Nil,
          servers = Nil,
          options = Options()),
        Backend(
          name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050::sava.port",
          mode = Interface.Mode.tcp,
          proxyServers = Nil,
          servers = HaProxyServer(
            name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050",
            host = "192.168.99.100",
            port = 33002,
            weight = 100) :: Nil,
          options = Options())
      ))

    actual shouldBe expected
    compare(HaProxyConfigurationTemplate(HaProxy(actual.frontends, actual.backends)).toString(), "configuration_4.txt")
  }

  it should "serialize A/B services to HAProxy configuration" in {
    val actual = convert(List(
      Gateway(
        name = "cd10460f-ca44-49c6-9965-f66c27acd478_sava_8080",
        port = 33001,
        protocol = "http",
        filters = Nil,
        services = List(
          Service(
            name = "sava:1.0.0",
            weight = 90,
            servers = List(
              Server(
                name = "64435a223bddf1fa589135baa5e228090279c032",
                host = "192.168.99.100",
                port = 32772),
              Server(
                name = "52c84bbf28dcc96bd4c4167eeeb7ff0a69bfb6eb",
                host = "192.168.99.100",
                port = 32772),
              Server(
                name = "5ccec1ae37f9c8f9e8eb1267bc176155541ceeb7",
                host = "192.168.99.100",
                port = 32772))
          ),
          Service(
            name = "sava:1.1.0",
            weight = 10,
            servers = List(
              Server(
                name = "9019c00f1f7f641c4efc7a02c6f44e9f90d7750",
                host = "192.168.99.100",
                port = 32773),
              Server(
                name = "49594c26c89754450bd4f562946a69070a4aa887",
                host = "192.168.99.100",
                port = 32773)
            )))),
      Gateway(
        name = "cd10460f-ca44-49c6-9965-f66c27acd478_9050",
        port = 9050,
        protocol = "http",
        filters = Nil,
        services = Service(
          name = "sava.port",
          weight = 100,
          servers = Server(
            name = "cd10460f-ca44-49c6-9965-f66c27acd478_9050",
            host = "192.168.99.100",
            port = 33002) :: Nil
        ) :: Nil)
    ))

    val expected = HaProxy(List(
      Frontend(
        name = "cd10460f-ca44-49c6-9965-f66c27acd478_sava_8080",
        bindIp = Option("0.0.0.0"),
        bindPort = Option(33001),
        mode = Interface.Mode.http,
        unixSock = None,
        sockProtocol = None,
        options = Options(),
        filters = Nil,
        defaultBackend = "cd10460f-ca44-49c6-9965-f66c27acd478_sava_8080"),
      Frontend(
        name = "cd10460f-ca44-49c6-9965-f66c27acd478_sava_8080::sava:1.0.0",
        bindIp = None,
        bindPort = None,
        mode = Interface.Mode.http,
        unixSock = Option("/opt/vamp/820eb143a8d42dd08f028f36e6b8385a911b8cd8.sock"),
        sockProtocol = Option("accept-proxy"),
        options = Options(),
        filters = Nil,
        defaultBackend = "cd10460f-ca44-49c6-9965-f66c27acd478_sava_8080::sava:1.0.0"),
      Frontend(
        name = "cd10460f-ca44-49c6-9965-f66c27acd478_sava_8080::sava:1.1.0",
        bindIp = None,
        bindPort = None,
        mode = Interface.Mode.http,
        unixSock = Option("/opt/vamp/81233b74c4c856e8c4697d7acf152ca6989db4df.sock"),
        sockProtocol = Option("accept-proxy"),
        options = Options(),
        filters = Nil,
        defaultBackend = "cd10460f-ca44-49c6-9965-f66c27acd478_sava_8080::sava:1.1.0"),
      Frontend(
        name = "cd10460f-ca44-49c6-9965-f66c27acd478_9050",
        bindIp = Option("0.0.0.0"),
        bindPort = Option(9050),
        mode = Interface.Mode.http,
        unixSock = None,
        sockProtocol = None,
        options = Options(),
        filters = Nil,
        defaultBackend = "cd10460f-ca44-49c6-9965-f66c27acd478_9050"),
      Frontend(
        name = "cd10460f-ca44-49c6-9965-f66c27acd478_9050::sava.port",
        bindIp = None,
        bindPort = None,
        mode = Interface.Mode.http,
        unixSock = Option("/opt/vamp/590382622f3287cf7bd9584fc1aa43052a40d6cc.sock"),
        sockProtocol = Option("accept-proxy"),
        options = Options(),
        filters = Nil,
        defaultBackend = "cd10460f-ca44-49c6-9965-f66c27acd478_9050::sava.port")
    ),
      List(
        Backend(
          name = "cd10460f-ca44-49c6-9965-f66c27acd478_sava_8080",
          mode = Interface.Mode.http,
          proxyServers = List(
            ProxyServer(
              name = "cd10460f-ca44-49c6-9965-f66c27acd478_sava_8080::sava:1.0.0",
              unixSock = "/opt/vamp/820eb143a8d42dd08f028f36e6b8385a911b8cd8.sock",
              weight = 90
            ),
            ProxyServer(
              name = "cd10460f-ca44-49c6-9965-f66c27acd478_sava_8080::sava:1.1.0",
              unixSock = "/opt/vamp/81233b74c4c856e8c4697d7acf152ca6989db4df.sock",
              weight = 10
            )),
          servers = Nil,
          options = Options()),
        Backend(
          name = "cd10460f-ca44-49c6-9965-f66c27acd478_sava_8080::sava:1.0.0",
          mode = Interface.Mode.http,
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
          options = Options()),
        Backend(
          name = "cd10460f-ca44-49c6-9965-f66c27acd478_sava_8080::sava:1.1.0",
          mode = Interface.Mode.http,
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
          options = Options()),
        Backend(
          name = "cd10460f-ca44-49c6-9965-f66c27acd478_9050",
          mode = Interface.Mode.http,
          proxyServers = ProxyServer(
            name = "cd10460f-ca44-49c6-9965-f66c27acd478_9050::sava.port",
            unixSock = "/opt/vamp/590382622f3287cf7bd9584fc1aa43052a40d6cc.sock",
            weight = 100
          ) :: Nil,
          servers = Nil,
          options = Options()),
        Backend(
          name = "cd10460f-ca44-49c6-9965-f66c27acd478_9050::sava.port",
          mode = Interface.Mode.http,
          proxyServers = Nil,
          servers = HaProxyServer(
            name = "cd10460f-ca44-49c6-9965-f66c27acd478_9050",
            host = "192.168.99.100",
            port = 33002,
            weight = 100) :: Nil,
          options = Options())
      ))

    actual shouldBe expected
    compare(HaProxyConfigurationTemplate(HaProxy(actual.frontends, actual.backends)).toString(), "configuration_5.txt")
  }

  it should "serialize services with dependency to HAProxy configuration" in {
    val actual = convert(List(
      Gateway(
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
      Gateway(
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
      Gateway(
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

    val expected = HaProxy(List(
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
        unixSock = Option("/opt/vamp/1c06647def2154787008cb74a4e9cdb0d414d5d8.sock"),
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
        unixSock = Option("/opt/vamp/83bea7dadc8ccb3d126c78d04b59acc9b9caa6df.sock"),
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
        unixSock = Option("/opt/vamp/7d8d614f8c1edab2bbc92a03efadd4e4e8275cee.sock"),
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
              unixSock = "/opt/vamp/1c06647def2154787008cb74a4e9cdb0d414d5d8.sock",
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
              unixSock = "/opt/vamp/83bea7dadc8ccb3d126c78d04b59acc9b9caa6df.sock",
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
            unixSock = "/opt/vamp/7d8d614f8c1edab2bbc92a03efadd4e4e8275cee.sock",
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

    actual shouldBe expected
    compare(HaProxyConfigurationTemplate(HaProxy(actual.frontends, actual.backends)).toString(), "configuration_6.txt")
  }

  it should "convert filters" in {
    implicit val route = Gateway("", 0, "", Nil, Nil)

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
        filter(Filter(None, input._1, "")) match {
          case HaProxyFilter(_, condition, _, negate) ⇒
            input._2 shouldBe condition
            input._3 shouldBe negate
        }
      }
  }

  it should "serialize service with filters to HAProxy configuration" in {
    val actual = convert(List(
      Gateway(
        name = "6b606985-1414-41bb-911c-825955360a39_sava_8080",
        port = 33000,
        protocol = "http",
        filters = List(
          Filter(
            name = None,
            condition = "user-agent != ie",
            destination = "sava:1.0.0"
          ), Filter(
            name = None,
            condition = "user-agent = chrome",
            destination = "sava:1.0.0"
          ), Filter(
            name = None,
            condition = "cookie group contains admin",
            destination = "sava:1.0.0"
          ), Filter(
            name = None,
            condition = "has header x-allow",
            destination = "sava:1.0.0"
          )),
        services = List(
          Service(
            name = "sava:1.0.0",
            weight = 100,
            servers = List(
              Server(
                name = "64435a223bddf1fa589135baa5e228090279c032",
                host = "192.168.99.100",
                port = 32776))
          )))))

    val expected = HaProxy(List(
      Frontend(
        name = "6b606985-1414-41bb-911c-825955360a39_sava_8080",
        bindIp = Option("0.0.0.0"),
        bindPort = Option(33000),
        mode = Interface.Mode.http,
        unixSock = None,
        sockProtocol = None,
        options = Options(),
        filters = List(
          HaProxyFilter(
            name = "624dc0f1a5754e94",
            condition = "hdr_sub(user-agent) ie",
            destination = "6b606985-1414-41bb-911c-825955360a39_sava_8080::sava:1.0.0",
            negate = true
          ),
          HaProxyFilter(
            name = "3cb39f8aae4d99da",
            condition = "hdr_sub(user-agent) chrome",
            destination = "6b606985-1414-41bb-911c-825955360a39_sava_8080::sava:1.0.0"
          ),
          HaProxyFilter(
            name = "445a3abaca79c140",
            condition = "cook_sub(group) admin",
            destination = "6b606985-1414-41bb-911c-825955360a39_sava_8080::sava:1.0.0"
          ),
          HaProxyFilter(
            name = "feb4c187ccc342ce",
            condition = "hdr_cnt(x-allow) gt 0",
            destination = "6b606985-1414-41bb-911c-825955360a39_sava_8080::sava:1.0.0"
          )
        ),
        defaultBackend = "6b606985-1414-41bb-911c-825955360a39_sava_8080"),
      Frontend(
        name = "6b606985-1414-41bb-911c-825955360a39_sava_8080::sava:1.0.0",
        bindIp = None,
        bindPort = None,
        mode = Interface.Mode.http,
        unixSock = Option("/opt/vamp/99ba9340c7565b91b87c80d7da989fc35754a383.sock"),
        sockProtocol = Option("accept-proxy"),
        options = Options(),
        filters = Nil,
        defaultBackend = "6b606985-1414-41bb-911c-825955360a39_sava_8080::sava:1.0.0"
      )),
      List(
        Backend(
          name = "6b606985-1414-41bb-911c-825955360a39_sava_8080",
          mode = Interface.Mode.http,
          proxyServers = List(
            ProxyServer(
              name = "6b606985-1414-41bb-911c-825955360a39_sava_8080::sava:1.0.0",
              unixSock = "/opt/vamp/99ba9340c7565b91b87c80d7da989fc35754a383.sock",
              weight = 100
            )),
          servers = Nil,
          options = Options()),
        Backend(
          name = "6b606985-1414-41bb-911c-825955360a39_sava_8080::sava:1.0.0",
          mode = Interface.Mode.http,
          proxyServers = Nil,
          servers = List(
            HaProxyServer(
              name = "64435a223bddf1fa589135baa5e228090279c032",
              host = "192.168.99.100",
              port = 32776,
              weight = 100)),
          options = Options())
      ))

    actual shouldBe expected
    compare(HaProxyConfigurationTemplate(HaProxy(actual.frontends, actual.backends)).toString(), "configuration_7.txt")
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
