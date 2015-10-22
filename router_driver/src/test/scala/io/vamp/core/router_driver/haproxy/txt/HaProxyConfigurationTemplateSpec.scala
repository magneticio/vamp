package io.vamp.core.router_driver.haproxy.txt

import io.vamp.core.router_driver.haproxy.HaProxyInterface.Mode
import io.vamp.core.router_driver.haproxy._
import io.vamp.core.router_driver.{ Route, Server, Service }
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{ FlatSpec, Matchers }

import scala.io.Source
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class HaProxyConfigurationTemplateSpec extends FlatSpec with Matchers with Route2HaProxyConverter {

  "HaProxyConfiguration" should "be serialized to valid HAProxy configuration" in {

    val options = HaProxyOptions(
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
      mode = Mode.http,
      unixSock = Option("/tmp/vamp_test_be_1_a.sock"),
      sockProtocol = Option("accept-proxy"),
      options = options,
      filters = filters,
      defaultBackend = "test_be_1"
    ) :: Nil

    val servers1 = HaProxyProxyServer(
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
      options = options
    ) :: Backend(
        name = "name2",
        mode = Mode.http,
        proxyServers = Nil,
        servers = servers2,
        options = options
      ) :: Nil

    compare(HaProxyConfigurationTemplate(HaProxyConfiguration(
      pidFile = "/opt/docker/data/haproxy-private.pid",
      statsSocket = "/opt/docker/data/haproxy.stats.sock",
      frontends = frontends,
      backends = backends,
      errorDir = "/error")
    ).toString(), "configuration_1.txt")
  }

  it should "serialize single service http route to HAProxy configuration" in {

    val model = convert(Route(
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

    model shouldBe HaProxyModel(List(
      Frontend(
        name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080",
        bindIp = Option("0.0.0.0"),
        bindPort = Option(33000),
        mode = HaProxyInterface.Mode.http,
        unixSock = None,
        sockProtocol = None,
        options = HaProxyOptions(),
        filters = Nil,
        defaultBackend = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080"),
      Frontend(
        name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080::sava:1.0.0",
        bindIp = None,
        bindPort = None,
        mode = HaProxyInterface.Mode.http,
        unixSock = Option("/opt/docker/data/651a9b8aa0b263752502e881c0da1da2ba4e0a8a.sock"),
        sockProtocol = Option("accept-proxy"),
        options = HaProxyOptions(),
        filters = Nil,
        defaultBackend = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080::sava:1.0.0")
    ), List(
      Backend(
        name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080",
        mode = HaProxyInterface.Mode.http,
        proxyServers = HaProxyProxyServer(
          name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080::sava:1.0.0",
          unixSock = "/opt/docker/data/651a9b8aa0b263752502e881c0da1da2ba4e0a8a.sock",
          weight = 100
        ) :: Nil,
        servers = Nil,
        options = HaProxyOptions()),
      Backend(
        name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080::sava:1.0.0",
        mode = HaProxyInterface.Mode.http,
        proxyServers = Nil,
        servers = HaProxyServer(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          host = "192.168.99.100",
          port = 32768,
          weight = 100) :: Nil,
        options = HaProxyOptions())
    ))

    compare(HaProxyConfigurationTemplate(HaProxyConfiguration(
      pidFile = "/opt/docker/data/haproxy-private.pid",
      statsSocket = "/opt/docker/data/haproxy.stats.sock",
      frontends = model.frontends,
      backends = model.backends,
      errorDir = "/opt/docker/configuration/error_pages")
    ).toString(), "configuration_2.txt")
  }

  it should "serialize single service tcp route to HAProxy configuration" in {
    val model = convert(Route(
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

    model shouldBe HaProxyModel(List(
      Frontend(
        name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080",
        bindIp = Option("0.0.0.0"),
        bindPort = Option(33000),
        mode = HaProxyInterface.Mode.tcp,
        unixSock = None,
        sockProtocol = None,
        options = HaProxyOptions(),
        filters = Nil,
        defaultBackend = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080"),
      Frontend(
        name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080::sava:1.0.0",
        bindIp = None,
        bindPort = None,
        mode = HaProxyInterface.Mode.tcp,
        unixSock = Option("/opt/docker/data/651a9b8aa0b263752502e881c0da1da2ba4e0a8a.sock"),
        sockProtocol = Option("accept-proxy"),
        options = HaProxyOptions(),
        filters = Nil,
        defaultBackend = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080::sava:1.0.0")
    ),
      List(
        Backend(
          name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080",
          mode = HaProxyInterface.Mode.tcp,
          proxyServers = HaProxyProxyServer(
            name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080::sava:1.0.0",
            unixSock = "/opt/docker/data/651a9b8aa0b263752502e881c0da1da2ba4e0a8a.sock",
            weight = 100
          ) :: Nil,
          servers = Nil,
          options = HaProxyOptions()),
        Backend(
          name = "3267f8c0-d717-4b8c-bca7-665d9d9294b7_sava_8080::sava:1.0.0",
          mode = HaProxyInterface.Mode.tcp,
          proxyServers = Nil,
          servers = HaProxyServer(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 32768,
            weight = 100) :: Nil,
          options = HaProxyOptions())
      ))

    compare(HaProxyConfigurationTemplate(HaProxyConfiguration(
      pidFile = "/opt/docker/data/haproxy-private.pid",
      statsSocket = "/opt/docker/data/haproxy.stats.sock",
      frontends = model.frontends,
      backends = model.backends,
      errorDir = "/opt/docker/configuration/error_pages")
    ).toString(), "configuration_3.txt")
  }

  it should "serialize single service route with single endpoint to HAProxy configuration" in {
    val model = convert(List(
      Route(
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
      Route(
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

    model shouldBe HaProxyModel(List(
      Frontend(
        name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_sava_8080",
        bindIp = Option("0.0.0.0"),
        bindPort = Option(33002),
        mode = HaProxyInterface.Mode.http,
        unixSock = None,
        sockProtocol = None,
        options = HaProxyOptions(),
        filters = Nil,
        defaultBackend = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_sava_8080"),
      Frontend(
        name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_sava_8080::sava:1.0.0",
        bindIp = None,
        bindPort = None,
        mode = HaProxyInterface.Mode.http,
        unixSock = Option("/opt/docker/data/a88b2dabfa50419d1db522d80ff74f782e24d006.sock"),
        sockProtocol = Option("accept-proxy"),
        options = HaProxyOptions(),
        filters = Nil,
        defaultBackend = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_sava_8080::sava:1.0.0"),
      Frontend(
        name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050",
        bindIp = Option("0.0.0.0"),
        bindPort = Option(9050),
        mode = HaProxyInterface.Mode.tcp,
        unixSock = None,
        sockProtocol = None,
        options = HaProxyOptions(),
        filters = Nil,
        defaultBackend = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050"),
      Frontend(
        name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050::sava.port",
        bindIp = None,
        bindPort = None,
        mode = HaProxyInterface.Mode.tcp,
        unixSock = Option("/opt/docker/data/c33b372cdc5daeb780b2f5ca3e1ca59a7320db90.sock"),
        sockProtocol = Option("accept-proxy"),
        options = HaProxyOptions(),
        filters = Nil,
        defaultBackend = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050::sava.port")
    ),
      List(
        Backend(
          name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_sava_8080",
          mode = HaProxyInterface.Mode.http,
          proxyServers = HaProxyProxyServer(
            name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_sava_8080::sava:1.0.0",
            unixSock = "/opt/docker/data/a88b2dabfa50419d1db522d80ff74f782e24d006.sock",
            weight = 100
          ) :: Nil,
          servers = Nil,
          options = HaProxyOptions()),
        Backend(
          name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_sava_8080::sava:1.0.0",
          mode = HaProxyInterface.Mode.http,
          proxyServers = Nil,
          servers = HaProxyServer(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 32770,
            weight = 100) :: Nil,
          options = HaProxyOptions()),
        Backend(
          name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050",
          mode = HaProxyInterface.Mode.tcp,
          proxyServers = HaProxyProxyServer(
            name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050::sava.port",
            unixSock = "/opt/docker/data/c33b372cdc5daeb780b2f5ca3e1ca59a7320db90.sock",
            weight = 100
          ) :: Nil,
          servers = Nil,
          options = HaProxyOptions()),
        Backend(
          name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050::sava.port",
          mode = HaProxyInterface.Mode.tcp,
          proxyServers = Nil,
          servers = HaProxyServer(
            name = "5b2c2c20-c073-4180-8942-2c3d5ede74fb_9050",
            host = "192.168.99.100",
            port = 33002,
            weight = 100) :: Nil,
          options = HaProxyOptions())
      ))

    compare(HaProxyConfigurationTemplate(HaProxyConfiguration(
      pidFile = "/opt/docker/data/haproxy-private.pid",
      statsSocket = "/opt/docker/data/haproxy.stats.sock",
      frontends = model.frontends,
      backends = model.backends,
      errorDir = "/opt/docker/configuration/error_pages")
    ).toString(), "configuration_4.txt")
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
