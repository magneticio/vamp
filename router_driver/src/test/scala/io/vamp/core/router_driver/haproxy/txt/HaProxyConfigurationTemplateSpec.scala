package io.vamp.core.router_driver.haproxy.txt

import io.vamp.core.router_driver.haproxy.HaProxyInterface.Mode
import io.vamp.core.router_driver.haproxy._
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class HaProxyConfigurationTemplateSpec extends HaProxySpec {

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

    val frontends = HaProxyFrontend(
      name = "name",
      bindIp = Some("0.0.0.0"),
      bindPort = Option(8080),
      mode = Mode.http,
      unixSock = Option("/tmp/vamp_test_be_1_a.sock"),
      sockProtocol = Option("accept-proxy"),
      options = options,
      httpQuota = Option(HaProxyHttpQuota("1s", 10000, "10s")),
      tcpQuota = Option(HaProxyTcpQuota("1s", 10000, "10s")),
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

    val backends = HaProxyBackend(
      name = "name1",
      mode = Mode.http,
      proxyServers = servers1,
      servers = Nil,
      options = options
    ) :: HaProxyBackend(
      name = "name2",
      mode = Mode.http,
      proxyServers = Nil,
      servers = servers2,
      options = options
    ) :: Nil

    HaProxyConfigurationTemplate(HaProxyConfiguration(
      pid = 33000,
      statsSocket = "haproxy_stat",
      frontends = frontends,
      backends = backends,
      errorDir = "/error")
    ).toString shouldBe res("configuration_1.txt")
  }
}
