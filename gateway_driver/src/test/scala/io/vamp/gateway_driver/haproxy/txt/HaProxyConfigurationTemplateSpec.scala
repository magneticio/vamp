package io.vamp.gateway_driver.haproxy.txt

import io.vamp.gateway_driver.haproxy.{ Filter ⇒ HaProxyFilter, Server ⇒ HaProxyServer, _ }
import io.vamp.model.artifact._
import io.vamp.model.reader.Percentage
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{ FlatSpec, Matchers }

import scala.io.Source
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class HaProxyConfigurationTemplateSpec extends FlatSpec with Matchers with HaProxyGatewayMarshaller {

  override lazy val path = Nil

  override lazy val version = "1.6"

  override lazy val info = super[FlatSpec].info

  override val httpLogFormat = """{"ci":"%ci","cp":%cp,"t":"%t","ft":"%ft","b":"%b","s":"%s","Tq":%Tq,"Tw":%Tw,"Tc":%Tc,"Tr":%Tr,"Tt":%Tt,"ST":%ST,"B":%B,"CC":"%CC","CS":"%CS","tsc":"%tsc","ac":%ac,"fc":%fc,"bc":%bc,"sc":%sc,"rc":%rc,"sq":%sq,"bq":%bq,"hr":"%hr","hs":"%hs","r":%{+Q}r}"""

  override val tcpLogFormat = """{"ci":"%ci","cp":%cp,"t":"%t","ft":"%ft","b":"%b","s":"%s","Tw":%Tw,"Tc":%Tc,"Tt":%Tt,"B":%B,"ts":"%ts","ac":%ac,"fc":%fc,"bc":%bc,"sc":%sc,"rc":%rc,"sq":%sq,"bq":%bq}"""

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

    val servers1 = ProxyServer(
      name = "server1",
      lookup = "server1",
      unixSock = "/tmp/vamp_test_be_1_a.sock",
      weight = 100
    ) :: Nil

    val servers2 = HaProxyServer(
      name = "test_be1_a_2",
      lookup = "test_be1_a_2",
      host = "192.168.59.103",
      port = 8082,
      weight = 100,
      checkInterval = Option(10)
    ) :: Nil

    val backends = Backend(
      name = "name1",
      lookup = "name1",
      mode = Mode.http,
      proxyServers = servers1,
      servers = Nil,
      rewrites = Nil,
      sticky = false,
      balance = "roundrobin",
      options = options
    ) :: Backend(
        name = "name2",
        lookup = "name2",
        mode = Mode.http,
        proxyServers = Nil,
        servers = servers2,
        rewrites = Rewrite("/images/%[path]", "p_ext_jpg path_end -i .jpg") :: Nil,
        sticky = false,
        balance = "roundrobin",
        options = options
      ) :: Nil

    val filters = HaProxyFilter(
      name = "ie",
      acls = new HaProxyAclResolver() {} resolve ("hdr_sub(user-agent) Firefox" :: "hdr_sub(user-agent) Chrome" :: Nil),
      destination = backends.head
    ) :: Nil

    val frontends = Frontend(
      name = "name",
      lookup = "name",
      bindIp = Some("0.0.0.0"),
      bindPort = Option(8080),
      mode = Mode.http,
      unixSock = Option("/tmp/vamp_test_be_1_a.sock"),
      sockProtocol = Option("accept-proxy"),
      options = options,
      filters = filters,
      defaultBackend = backends.head
    ) :: Nil

    compare(HaProxyConfigurationTemplate(HaProxy(frontends, backends, version, tcpLogFormat, httpLogFormat)).toString(), "configuration_1.txt")
  }

  it should "serialize single service http route to HAProxy configuration" in {
    val converted = convert(Gateway(
      name = "vamp/sava/port/_",
      port = Port(33000),
      sticky = None,
      routes = DefaultRoute(
        name = "vamp/sava/port/_/vamp/sava/sava:1.0.0/port",
        path = GatewayPath("vamp/sava/sava:1.0.0/port"),
        weight = Option(Percentage(100)),
        filters = Nil,
        rewrites = Nil,
        balance = None,
        targets = RouteTarget(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          host = "192.168.99.100",
          port = 32768
        ) :: Nil
      ) :: Nil))

    compare(HaProxyConfigurationTemplate(HaProxy(converted.frontends, converted.backends, version, tcpLogFormat, httpLogFormat)).toString(), "configuration_2.txt")
  }

  it should "serialize single service tcp route to HAProxy configuration" in {
    val converted = convert(Gateway(
      name = "vamp/sava/port/_",
      port = Port("33000/tcp"),
      sticky = None,
      routes = DefaultRoute(
        name = "vamp/sava/port/_/vamp/sava/sava:1.0.0/port",
        path = GatewayPath("vamp/sava/sava:1.0.0/port"),
        weight = Option(Percentage(100)),
        filters = Nil,
        rewrites = Nil,
        balance = None,
        targets = RouteTarget(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          host = "192.168.99.100",
          port = 32768
        ) :: Nil
      ) :: Nil))

    compare(HaProxyConfigurationTemplate(HaProxy(converted.frontends, converted.backends, version, tcpLogFormat, httpLogFormat)).toString(), "configuration_3.txt")
  }

  it should "serialize single service route with single endpoint to HAProxy configuration" in {
    val converted = convert(List(
      Gateway(
        name = "vamp/sava/port/_",
        port = Port("33002"),
        sticky = None,
        routes = DefaultRoute(
          name = "vamp/sava/port/_/vamp/sava/sava:1.0.0/port",
          path = GatewayPath("vamp/sava/sava:1.0.0/port"),
          weight = Option(Percentage(100)),
          filters = Nil,
          rewrites = Nil,
          balance = None,
          targets = RouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 32770
          ) :: Nil)
          :: Nil
      ),
      Gateway(
        name = "vamp/port/_/_",
        port = Port("9050/tcp"),
        sticky = None,
        routes = DefaultRoute(
          name = "vamp/sava/port/_",
          path = GatewayPath("vamp/sava/port/_"),
          weight = Option(Percentage(100)),
          filters = Nil,
          rewrites = Nil,
          balance = None,
          targets = RouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 33002
          ) :: Nil)
          :: Nil
      )
    ))

    compare(HaProxyConfigurationTemplate(HaProxy(converted.frontends, converted.backends, version, tcpLogFormat, httpLogFormat)).toString(), "configuration_4.txt")
  }

  it should "serialize A/B services to HAProxy configuration" in {
    val converted = convert(List(
      Gateway(
        name = "vamp/sava/port/_",
        port = Port(33001),
        sticky = None,
        routes = List(
          DefaultRoute(
            name = "vamp/sava/port/_/vamp/sava/sava:1.0.0/port",
            path = GatewayPath("vamp/sava/sava:1.0.0/port"),
            weight = Option(Percentage(90)),
            filters = Nil,
            rewrites = Nil,
            balance = None,
            targets = List(
              RouteTarget(
                name = "64435a223bddf1fa589135baa5e228090279c032",
                host = "192.168.99.100",
                port = 32772
              ), RouteTarget(
                name = "52c84bbf28dcc96bd4c4167eeeb7ff0a69bfb6eb",
                host = "192.168.99.100",
                port = 32772
              ), RouteTarget(
                name = "5ccec1ae37f9c8f9e8eb1267bc176155541ceeb7",
                host = "192.168.99.100",
                port = 32772
              ))),
          DefaultRoute(
            name = "vamp/sava/port/_/vamp/sava/sava:1.1.0/port",
            path = GatewayPath("vamp/sava/sava:1.1.0/port"),
            weight = Option(Percentage(10)),
            filters = Nil,
            rewrites = Nil,
            balance = None,
            targets = List(
              RouteTarget(
                name = "9019c00f1f7f641c4efc7a02c6f44e9f90d7750",
                host = "192.168.99.100",
                port = 32773
              ), RouteTarget(
                name = "49594c26c89754450bd4f562946a69070a4aa887",
                host = "192.168.99.100",
                port = 32773
              )))
        )
      ),
      Gateway(
        name = "vamp/port/_/_",
        port = Port("9050/http"),
        sticky = None,
        routes = DefaultRoute(
          name = "vamp/sava/port/_",
          path = GatewayPath("vamp/sava/port/_"),
          weight = Option(Percentage(100)),
          filters = Nil,
          rewrites = Nil,
          balance = None,
          targets = RouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 33002
          ) :: Nil)
          :: Nil
      )
    ))

    compare(HaProxyConfigurationTemplate(HaProxy(converted.frontends, converted.backends, version, tcpLogFormat, httpLogFormat)).toString(), "configuration_5.txt")
  }

  it should "serialize services with dependency to HAProxy configuration" in {
    val converted = convert(List(
      Gateway(
        name = "vamp/backend/port",
        port = Port(33003),
        sticky = None,
        routes = DefaultRoute(
          name = "vamp/sava/sava-backend:1.3.0/port",
          path = GatewayPath("vamp/sava/sava-backend:1.3.0/port"),
          weight = Option(Percentage(100)),
          filters = Nil,
          rewrites = Nil,
          balance = None,
          targets = RouteTarget(
            name = "57c4e3d2cbb8f0db907f5e16ceed9a4241d7e117",
            host = "192.168.99.100",
            port = 32770
          ) :: Nil)
          :: Nil
      ),
      Gateway(
        name = "vamp/sava/port/_",
        port = Port("33002"),
        sticky = None,
        routes = DefaultRoute(
          name = "vamp/sava/sava-frontend:1.3.0/port",
          path = GatewayPath("vamp/sava/sava-frontend:1.3.0/port"),
          weight = Option(Percentage(100)),
          filters = Nil,
          rewrites = Nil,
          balance = None,
          targets = RouteTarget(
            name = "f1638245acf2ebe6db56984a85b48f6db8c74607",
            host = "192.168.99.100",
            port = 32771
          ) :: Nil)
          :: Nil
      ),
      Gateway(
        name = "vamp/port/_/_",
        port = Port("9050/http"),
        sticky = None,
        routes = DefaultRoute(
          name = "vamp/sava/port/_",
          path = GatewayPath("vamp/sava/port/_"),
          weight = Option(Percentage(100)),
          filters = Nil,
          rewrites = Nil,
          balance = None,
          targets = RouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 33002
          ) :: Nil)
          :: Nil
      )
    ))

    compare(HaProxyConfigurationTemplate(HaProxy(converted.frontends, converted.backends, version, tcpLogFormat, httpLogFormat)).toString(), "configuration_6.txt")
  }

  it should "convert filters" in {
    val route = DefaultRoute("sava", GatewayPath("sava"), None, Nil, Nil, None)
    val backends = Backend("vamp://sava", "im_ec6129b90571c3f9737d86f16e82eabe2a3ae820", Mode.http, Nil, Nil, Nil, sticky = false, "", Options()) :: Nil

    List(
      ("hdr_sub(user-agent) Android", "hdr_sub(user-agent) Android"),
      ("user-agent=Android", "hdr_sub(user-agent) Android"),
      ("user-agent!=Android", "hdr_sub(user-agent) Android"),
      ("User-Agent=Android", "hdr_sub(user-agent) Android"),
      ("user-agent = Android", "hdr_sub(user-agent) Android"),
      ("user-agent  =  Android", "hdr_sub(user-agent) Android"),
      ("user.agent = Ios", "hdr_sub(user-agent) Ios"),
      ("host = www.google.com", "hdr_str(host) www.google.com"),
      ("host != www.google.com", "hdr_str(host) www.google.com"),
      ("cookie MYCUSTOMER contains Value=good", "cook_sub(MYCUSTOMER) Value=good"),
      ("has cookie JSESSIONID", "cook(JSESSIONID) -m found"),
      ("misses cookie JSESSIONID", "cook_cnt(JSESSIONID) eq 0"),
      ("has header X-SPECIAL", "hdr_cnt(X-SPECIAL) gt 0"),
      ("misses header X-SPECIAL", "hdr_cnt(X-SPECIAL) eq 0")
    ) foreach { input ⇒
        filter(route.copy(filters = DefaultFilter("", input._1) :: Nil))(backends, Gateway("vamp", Port(0), None, Nil)) match {
          case HaProxyFilter(_, _, Some(acls)) ⇒
            acls.acls.head.definition shouldBe input._2
        }
      }
  }

  it should "serialize service with filters to HAProxy configuration" in {
    val converted = convert(
      Gateway(
        name = "vamp/sava/port/_",
        port = Port(33000),
        sticky = None,
        routes = DefaultRoute(
          name = "vamp/sava/port/_/vamp/sava/sava:1.0.0/port",
          path = GatewayPath("vamp/sava/sava:1.0.0/port"),
          weight = Option(Percentage(100)),
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
            )),
          rewrites = List(
            PathRewrite(
              name = "",
              path = "/images/%[path]",
              condition = "p_ext_jpg path_end -i .jpg"
            ), PathRewrite(
              name = "",
              path = "/img/%[path]",
              condition = "{ p_ext_jpg path_end -i .jpg } !{ p_folder_images path_beg -i /images/ }"
            )),
          balance = None,
          targets = RouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 32776
          ) :: Nil
        ) :: Nil)
    )

    compare(HaProxyConfigurationTemplate(HaProxy(converted.frontends, converted.backends, version, tcpLogFormat, httpLogFormat)).toString(), "configuration_7.txt")
  }

  it should "serialize A/B services to HAProxy configuration - sticky route" in {
    val converted = convert(List(
      Gateway(
        name = "vamp/sava/port/_",
        port = Port(33001),
        sticky = Some(Gateway.Sticky.Route),
        routes = List(
          DefaultRoute(
            name = "vamp/sava/port/_/vamp/sava/sava:1.0.0/port",
            path = GatewayPath("vamp/sava/sava:1.0.0/port"),
            weight = Option(Percentage(90)),
            filters = Nil,
            rewrites = Nil,
            balance = None,
            targets = List(
              RouteTarget(
                name = "64435a223bddf1fa589135baa5e228090279c032",
                host = "192.168.99.100",
                port = 32772
              ), RouteTarget(
                name = "52c84bbf28dcc96bd4c4167eeeb7ff0a69bfb6eb",
                host = "192.168.99.100",
                port = 32772
              ), RouteTarget(
                name = "5ccec1ae37f9c8f9e8eb1267bc176155541ceeb7",
                host = "192.168.99.100",
                port = 32772
              ))),
          DefaultRoute(
            name = "vamp/sava/port/_/vamp/sava/sava:1.1.0/port",
            path = GatewayPath("vamp/sava/sava:1.1.0/port"),
            weight = Option(Percentage(10)),
            filters = Nil,
            rewrites = Nil,
            balance = None,
            targets = List(
              RouteTarget(
                name = "9019c00f1f7f641c4efc7a02c6f44e9f90d7750",
                host = "192.168.99.100",
                port = 32773
              ), RouteTarget(
                name = "49594c26c89754450bd4f562946a69070a4aa887",
                host = "192.168.99.100",
                port = 32773
              )))
        )
      ),
      Gateway(
        name = "vamp/port/_/_",
        port = Port("9050/http"),
        sticky = None,
        routes = DefaultRoute(
          name = "vamp/sava/port/_",
          path = GatewayPath("vamp/sava/port/_"),
          weight = Option(Percentage(100)),
          filters = Nil,
          rewrites = Nil,
          balance = None,
          targets = RouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 33002
          ) :: Nil)
          :: Nil
      )
    ))

    compare(HaProxyConfigurationTemplate(HaProxy(converted.frontends, converted.backends, version, tcpLogFormat, httpLogFormat)).toString(), "configuration_8.txt")
  }

  it should "serialize A/B services to HAProxy configuration - sticky instance" in {
    val converted = convert(List(
      Gateway(
        name = "vamp/sava/port/_",
        port = Port(33001),
        sticky = Some(Gateway.Sticky.Instance),
        routes = List(
          DefaultRoute(
            name = "vamp/sava/port/_/vamp/sava/sava:1.0.0/port",
            path = GatewayPath("vamp/sava/sava:1.0.0/port"),
            weight = Option(Percentage(90)),
            filters = Nil,
            rewrites = Nil,
            balance = None,
            targets = List(
              RouteTarget(
                name = "64435a223bddf1fa589135baa5e228090279c032",
                host = "192.168.99.100",
                port = 32772
              ), RouteTarget(
                name = "52c84bbf28dcc96bd4c4167eeeb7ff0a69bfb6eb",
                host = "192.168.99.100",
                port = 32772
              ), RouteTarget(
                name = "5ccec1ae37f9c8f9e8eb1267bc176155541ceeb7",
                host = "192.168.99.100",
                port = 32772
              ))),
          DefaultRoute(
            name = "vamp/sava/port/_/vamp/sava/sava:1.1.0/port",
            path = GatewayPath("vamp/sava/sava:1.1.0/port"),
            weight = Option(Percentage(10)),
            filters = Nil,
            rewrites = Nil,
            balance = None,
            targets = List(
              RouteTarget(
                name = "9019c00f1f7f641c4efc7a02c6f44e9f90d7750",
                host = "192.168.99.100",
                port = 32773
              ), RouteTarget(
                name = "49594c26c89754450bd4f562946a69070a4aa887",
                host = "192.168.99.100",
                port = 32773
              )))
        )
      ),
      Gateway(
        name = "vamp/port/_/_",
        port = Port("9050/http"),
        sticky = None,
        routes = DefaultRoute(
          name = "vamp/sava/port/_",
          path = GatewayPath("vamp/sava/port/_"),
          weight = Option(Percentage(100)),
          filters = Nil,
          rewrites = Nil,
          balance = None,
          targets = RouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.100",
            port = 33002
          ) :: Nil)
          :: Nil
      )
    ))

    compare(HaProxyConfigurationTemplate(HaProxy(converted.frontends, converted.backends, version, tcpLogFormat, httpLogFormat)).toString(), "configuration_9.txt")
  }

  it should "serialize A/B testing on deployments" in {
    val converted = convert(List(
      Gateway(
        name = "vamp:1.x/sava/port",
        port = Port(33001),
        sticky = None,
        routes = DefaultRoute(
          name = "vamp:1.x/sava/sava:1.0.0/port",
          path = GatewayPath("vamp:1.x/sava/sava:1.0.0/port"),
          weight = Option(Percentage(100)),
          filters = Nil,
          rewrites = Nil,
          balance = None,
          targets = RouteTarget(
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
        routes = DefaultRoute(
          name = "vamp:2.x/sava/sava:2.0.0/port",
          path = GatewayPath("vamp:2.x/sava/sava:2.0.0/port"),
          weight = Option(Percentage(100)),
          filters = Nil,
          rewrites = Nil,
          balance = None,
          targets = RouteTarget(
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
          DefaultRoute(
            name = "vamp:1.x/sava/port",
            path = GatewayPath("vamp:1.x/sava/port"),
            weight = Option(Percentage(90)),
            filters = Nil,
            rewrites = Nil,
            balance = None,
            targets = List(
              RouteTarget(
                name = "64435a223bddf1fa589135baa5e228090279c032",
                host = "192.168.99.100",
                port = 32772
              ))),
          DefaultRoute(
            name = "vamp:2.x/sava/port",
            path = GatewayPath("vamp:2.x/sava/port"),
            weight = Option(Percentage(10)),
            filters = Nil,
            rewrites = Nil,
            balance = None,
            targets = List(
              RouteTarget(
                name = "9019c00f1f7f641c4efc7a02c6f44e9f90d7750",
                host = "192.168.99.100",
                port = 32773
              )))
        ))
    ))

    compare(HaProxyConfigurationTemplate(HaProxy(converted.frontends, converted.backends, version, tcpLogFormat, httpLogFormat)).toString(), "configuration_10.txt")
  }

  it should "serialize custom balance" in {
    val converted = convert(List(
      Gateway(
        name = "vamp:1.x/sava/port",
        port = Port(33001),
        sticky = None,
        routes = DefaultRoute(
          name = "vamp:1.x/sava/sava:1.0.0/port",
          path = GatewayPath("vamp:1.x/sava/sava:1.0.0/port"),
          weight = Option(Percentage(100)),
          filters = Nil,
          rewrites = Nil,
          balance = Some("first"),
          targets = RouteTarget(
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
        routes = DefaultRoute(
          name = "vamp:2.x/sava/sava:2.0.0/port",
          path = GatewayPath("vamp:2.x/sava/sava:2.0.0/port"),
          weight = Option(Percentage(100)),
          filters = Nil,
          rewrites = Nil,
          balance = Some("custom"),
          targets = RouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            host = "192.168.99.101",
            port = 32771
          ) :: Nil)
          :: Nil
      )
    ))

    compare(HaProxyConfigurationTemplate(HaProxy(converted.frontends, converted.backends, version, tcpLogFormat, httpLogFormat)).toString(), "configuration_11.txt")
  }

  private def compare(config: String, resource: String) = {
    def normalize(string: String): Array[String] = string.replaceAll("\\\n\\s*\\\n\\s*\\\n", "\n\n") match {
      case s ⇒ s.split('\n').map(_.trim).filter(_.nonEmpty).filterNot(_.startsWith("#")).map(_.replaceAll("\\s+", " "))
    }

    val actual = normalize(config)
    val expected = normalize(Source.fromURL(getClass.getResource(resource)).mkString)

    actual.length shouldBe expected.length

    actual.zip(expected).foreach { line ⇒
      line._1 shouldBe line._2
    }
  }
}
