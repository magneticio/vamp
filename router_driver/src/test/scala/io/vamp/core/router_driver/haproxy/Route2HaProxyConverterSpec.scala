package io.vamp.core.router_driver.haproxy

import io.vamp.core.router_driver.{ Route, Server, Service }
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class Route2HaProxyConverterSpec extends HaProxySpec with Route2HaProxyConverter {

  "Route2HaProxyConverter" should "serialize single service http route" in {
    convert(Route(
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
      ) :: Nil)) shouldBe HaProxyModel(List(
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
  }

  it should "serialize single service tcp route" in {
    convert(Route(
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
      ) :: Nil)) shouldBe HaProxyModel(List(
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
  }

  it should "serialize single service route with single endpoint" in {
    convert(List(
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
    )) shouldBe HaProxyModel(List(
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
  }
}
