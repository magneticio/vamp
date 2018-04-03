package io.vamp.gateway_driver.haproxy

import io.vamp.common.Namespace
import io.vamp.gateway_driver.haproxy.{ Condition ⇒ HaProxyCondition, Server ⇒ HaProxyServer }
import io.vamp.model.artifact._
import io.vamp.model.reader.Percentage
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{ FlatSpec, Matchers }

import scala.io.Source

@RunWith(classOf[JUnitRunner])
class HaProxyGatewayMarshallerSpec extends FlatSpec with Matchers {

  private val marshaller = new HaProxyGatewayMarshaller
  private val template = Source.fromURL(getClass.getResource("/io/vamp/gateway_driver/haproxy/template.twig")).mkString
  private implicit val namespace: Namespace = Namespace.empty

  "HaProxyConfiguration" should "be serialized to valid HAProxy configuration" in {

    val servers1 = ProxyServer(
      name = "server1",
      lookup = "server1",
      unixSock = "vamp_test_be_1_a.sock",
      weight = 100
    ) :: Nil

    val servers2 = HaProxyServer(
      name = "test_be1_a_2",
      lookup = "test_be1_a_2",
      url = Option("192.168.59.103:8082"),
      host = None,
      port = None,
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
      balance = "roundrobin"
    ) :: Backend(
      name = "name2",
      lookup = "name2",
      mode = Mode.http,
      proxyServers = Nil,
      servers = servers2,
      rewrites = Rewrite("/images/%[path]", "p_ext_jpg path_end -i .jpg") :: Nil,
      sticky = false,
      balance = "roundrobin"
    ) :: Nil

    val conditions = HaProxyCondition(
      name = "ie",
      acls = new HaProxyAclResolver() {} resolve "< hdr_sub(user-agent) Firefox > AND < hdr_sub(user-agent) Chrome >",
      destination = backends.head
    ) :: Nil

    val frontends = Frontend(
      name = "name",
      lookup = "name",
      bindIp = Some("0.0.0.0"),
      bindPort = Option(8080),
      mode = Mode.http,
      unixSock = Option("vamp_test_be_1_a.sock"),
      sockProtocol = Option("accept-proxy"),
      conditions = conditions,
      defaultBackend = backends.head
    ) :: Nil

    compare(marshaller.marshall(HaProxy(frontends, backends, Nil, Nil), template), "configuration_1.txt")
  }

  it should "serialize single service http route to HAProxy configuration" in {
    val converted = marshaller.convert(Gateway(
      name = "vamp/sava/port/_",
      metadata = Map(),
      port = Port(33000),
      service = None,
      sticky = None,
      virtualHosts = Nil,
      selector = None,
      routes = DefaultRoute(
        name = "vamp/sava/port/_/vamp/sava/sava:1.0.0/port",
        metadata = Map(),
        path = GatewayPath("vamp/sava/sava:1.0.0/port"),
        selector = None,
        weight = Option(Percentage(100)),
        conditionStrength = None,
        condition = None,
        rewrites = Nil,
        balance = None,
        targets = InternalRouteTarget(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          host = Some("aaa.bbb.ccc"),
          port = 32768
        ) :: Nil
      ) :: Nil
    ))

    compare(marshaller.marshall(HaProxy(converted.frontends, converted.backends, Nil, Nil), template), "configuration_2.txt")
  }

  it should "serialize single service tcp route to HAProxy configuration" in {
    val converted = marshaller.convert(Gateway(
      name = "vamp/sava/port/_",
      metadata = Map(),
      port = Port("33000/tcp"),
      service = None,
      sticky = None,
      virtualHosts = Nil,
      selector = None,
      routes = DefaultRoute(
        name = "vamp/sava/port/_/vamp/sava/sava:1.0.0/port",
        metadata = Map(),
        path = GatewayPath("vamp/sava/sava:1.0.0/port"),
        selector = None,
        weight = Option(Percentage(100)),
        conditionStrength = None,
        condition = None,
        rewrites = Nil,
        balance = None,
        targets = InternalRouteTarget(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          port = 32768
        ) :: Nil
      ) :: Nil
    ))

    compare(marshaller.marshall(HaProxy(converted.frontends, converted.backends, Nil, Nil), template), "configuration_3.txt")
  }

  it should "serialize single service route with single endpoint to HAProxy configuration" in {
    val converted = marshaller.convert(List(
      Gateway(
        name = "vamp/sava/port/_",
        metadata = Map(),
        port = Port("33002"),
        service = None,
        sticky = None,
        virtualHosts = Nil,
        selector = None,
        routes = DefaultRoute(
          name = "vamp/sava/port/_/vamp/sava/sava:1.0.0/port",
          metadata = Map(),
          path = GatewayPath("vamp/sava/sava:1.0.0/port"),
          selector = None,
          weight = Option(Percentage(100)),
          conditionStrength = None,
          condition = None,
          rewrites = Nil,
          balance = None,
          targets = InternalRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            port = 32770
          ) :: Nil
        )
          :: Nil
      ),
      Gateway(
        name = "vamp/port/_/_",
        metadata = Map(),
        port = Port("9050/tcp"),
        service = None,
        sticky = None,
        virtualHosts = Nil,
        selector = None,
        routes = DefaultRoute(
          name = "vamp/sava/port/_",
          metadata = Map(),
          path = GatewayPath("vamp/sava/port/_"),
          selector = None,
          weight = Option(Percentage(100)),
          conditionStrength = None,
          condition = None,
          rewrites = Nil,
          balance = None,
          targets = InternalRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            port = 33002
          ) :: Nil
        )
          :: Nil
      )
    ))

    compare(marshaller.marshall(HaProxy(converted.frontends, converted.backends, Nil, Nil), template), "configuration_4.txt")
  }

  it should "serialize A/B services to HAProxy configuration" in {
    val converted = marshaller.convert(List(
      Gateway(
        name = "vamp/sava/port/_",
        metadata = Map(),
        port = Port(33001),
        service = None,
        sticky = None,
        virtualHosts = Nil,
        selector = None,
        routes = List(
          DefaultRoute(
            name = "vamp/sava/port/_/vamp/sava/sava:1.0.0/port",
            metadata = Map(),
            path = GatewayPath("vamp/sava/sava:1.0.0/port"),
            selector = None,
            weight = Option(Percentage(90)),
            conditionStrength = None,
            condition = None,
            rewrites = Nil,
            balance = None,
            targets = List(
              InternalRouteTarget(
                name = "64435a223bddf1fa589135baa5e228090279c032",
                port = 32772
              ), InternalRouteTarget(
                name = "52c84bbf28dcc96bd4c4167eeeb7ff0a69bfb6eb",
                port = 32772
              ), InternalRouteTarget(
                name = "5ccec1ae37f9c8f9e8eb1267bc176155541ceeb7",
                port = 32772
              )
            )
          ),
          DefaultRoute(
            name = "vamp/sava/port/_/vamp/sava/sava:1.1.0/port",
            metadata = Map(),
            path = GatewayPath("vamp/sava/sava:1.1.0/port"),
            selector = None,
            weight = Option(Percentage(10)),
            conditionStrength = None,
            condition = None,
            rewrites = Nil,
            balance = None,
            targets = List(
              InternalRouteTarget(
                name = "9019c00f1f7f641c4efc7a02c6f44e9f90d7750",
                port = 32773
              ), InternalRouteTarget(
                name = "49594c26c89754450bd4f562946a69070a4aa887",
                port = 32773
              )
            )
          )
        )
      ),
      Gateway(
        name = "vamp/port/_/_",
        metadata = Map(),
        port = Port("9050/http"),
        service = None,
        sticky = None,
        virtualHosts = Nil,
        selector = None,
        routes = DefaultRoute(
          name = "vamp/sava/port/_",
          metadata = Map(),
          path = GatewayPath("vamp/sava/port/_"),
          selector = None,
          weight = Option(Percentage(100)),
          conditionStrength = None,
          condition = None,
          rewrites = Nil,
          balance = None,
          targets = InternalRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            port = 33002
          ) :: Nil
        )
          :: Nil
      )
    ))

    compare(marshaller.marshall(HaProxy(converted.frontends, converted.backends, Nil, Nil), template), "configuration_5.txt")
  }

  it should "serialize services with dependency to HAProxy configuration" in {
    val converted = marshaller.convert(List(
      Gateway(
        name = "vamp/backend/port",
        metadata = Map(),
        port = Port(33003),
        service = None,
        sticky = None,
        virtualHosts = Nil,
        selector = None,
        routes = DefaultRoute(
          name = "vamp/sava/sava-backend:1.3.0/port",
          metadata = Map(),
          path = GatewayPath("vamp/sava/sava-backend:1.3.0/port"),
          selector = None,
          weight = Option(Percentage(100)),
          conditionStrength = None,
          condition = None,
          rewrites = Nil,
          balance = None,
          targets = InternalRouteTarget(
            name = "57c4e3d2cbb8f0db907f5e16ceed9a4241d7e117",
            port = 32770
          ) :: Nil
        )
          :: Nil
      ),
      Gateway(
        name = "vamp/sava/port/_",
        metadata = Map(),
        port = Port("33002"),
        service = None,
        sticky = None,
        virtualHosts = Nil,
        selector = None,
        routes = DefaultRoute(
          name = "vamp/sava/sava-frontend:1.3.0/port",
          metadata = Map(),
          path = GatewayPath("vamp/sava/sava-frontend:1.3.0/port"),
          selector = None,
          weight = Option(Percentage(100)),
          conditionStrength = None,
          condition = None,
          rewrites = Nil,
          balance = None,
          targets = InternalRouteTarget(
            name = "f1638245acf2ebe6db56984a85b48f6db8c74607",
            port = 32771
          ) :: Nil
        )
          :: Nil
      ),
      Gateway(
        name = "vamp/port/_/_",
        metadata = Map(),
        port = Port("9050/http"),
        service = None,
        sticky = None,
        virtualHosts = Nil,
        selector = None,
        routes = DefaultRoute(
          name = "vamp/sava/port/_",
          metadata = Map(),
          path = GatewayPath("vamp/sava/port/_"),
          selector = None,
          weight = Option(Percentage(100)),
          conditionStrength = None,
          condition = None,
          rewrites = Nil,
          balance = None,
          targets = InternalRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            port = 33002
          ) :: Nil
        )
          :: Nil
      )
    ))

    compare(marshaller.marshall(HaProxy(converted.frontends, converted.backends, Nil, Nil), template), "configuration_6.txt")
  }

  it should "convert conditions" in {
    val route = DefaultRoute("sava", Map(), GatewayPath("sava"), None, None, None, None, Nil, None)
    val backends = Backend("vamp://sava", "im_ec6129b90571c3f9737d86f16e82eabe2a3ae820", Mode.http, Nil, Nil, Nil, sticky = false, "") :: Nil

    List(
      ("user-agent = Firefox", "req.fhdr(User-Agent) -m sub 'Firefox'"),
      ("user-agent = Safari", "req.fhdr(User-Agent) -m sub 'Safari'"),
      ("hdr_sub(user-agent) Android", "hdr_sub(user-agent) Android"),
      ("user-agent=Android", "req.fhdr(User-Agent) -m sub 'Android'"),
      ("user-agent!=Android", "req.fhdr(User-Agent) -m sub 'Android'"),
      ("User-Agent=Android", "req.fhdr(User-Agent) -m sub 'Android'"),
      ("user-agent = Android", "req.fhdr(User-Agent) -m sub 'Android'"),
      ("user-agent  =  Android", "req.fhdr(User-Agent) -m sub 'Android'"),
      ("user.agent = Ios", "req.fhdr(User-Agent) -m sub 'Ios'"),
      ("host = www.google.com", "req.hdr(host) www.google.com"),
      ("host != www.google.com", "req.hdr(host) www.google.com"),
      ("cookie MYCUSTOMER contains Value=good", "req.cook_sub(MYCUSTOMER) Value=good"),
      ("has cookie JSESSIONID", "req.cook(JSESSIONID) -m found"),
      ("misses cookie JSESSIONID", "req.cook_cnt(JSESSIONID) eq 0"),
      ("has header X-SPECIAL", "req.hdr_cnt(X-SPECIAL) gt 0"),
      ("misses header X-SPECIAL", "req.hdr_cnt(X-SPECIAL) eq 0")
    ) foreach { input ⇒
        marshaller.filter(route.copy(condition = Option(DefaultCondition("", metadata = Map(), input._1))))(namespace, backends, Gateway("vamp", metadata = Map(), Port(0), None, None, Nil, None, Nil)) match {
          case Some(Condition(_, _, Some(acls))) ⇒ acls.acls.head.definition shouldBe input._2
          case _                                 ⇒
        }
      }
  }

  it should "serialize service with conditions to HAProxy configuration" in {
    val converted = marshaller.convert(
      Gateway(
        name = "vamp/sava/port/_",
        metadata = Map(),
        port = Port(33000),
        service = None,
        sticky = None,
        virtualHosts = Nil,
        selector = None,
        routes = DefaultRoute(
          name = "vamp/sava/port/_/vamp/sava/sava:1.0.0/port",
          metadata = Map(),
          path = GatewayPath("vamp/sava/sava:1.0.0/port"),
          selector = None,
          weight = Option(Percentage(100)),
          conditionStrength = Option(Percentage(100)),
          condition = Option(
            DefaultCondition("", metadata = Map(), "user-agent != safari and user-agent = chrome and cookie group contains admin and has header x-allow")
          ),
          rewrites = List(
            PathRewrite(
              name = "",
              path = "/images/%[path]",
              condition = "p_ext_jpg path_end -i .jpg"
            ), PathRewrite(
              name = "",
              path = "/img/%[path]",
              condition = "{ p_ext_jpg path_end -i .jpg } !{ p_folder_images path_beg -i /images/ }"
            )
          ),
          balance = None,
          targets = InternalRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            port = 32776
          ) :: Nil
        ) :: Nil
      )
    )

    compare(marshaller.marshall(HaProxy(converted.frontends, converted.backends, Nil, Nil), template), "configuration_7.txt")
  }

  it should "serialize A/B services to HAProxy configuration - sticky route" in {
    val converted = marshaller.convert(List(
      Gateway(
        name = "vamp/sava/port/_",
        metadata = Map(),
        port = Port(33001),
        service = None,
        sticky = Some(Gateway.Sticky.Route),
        virtualHosts = Nil,
        selector = None,
        routes = List(
          DefaultRoute(
            name = "vamp/sava/port/_/vamp/sava/sava:1.0.0/port",
            metadata = Map(),
            path = GatewayPath("vamp/sava/sava:1.0.0/port"),
            selector = None,
            weight = Option(Percentage(90)),
            conditionStrength = None,
            condition = None,
            rewrites = Nil,
            balance = None,
            targets = List(
              InternalRouteTarget(
                name = "64435a223bddf1fa589135baa5e228090279c032",
                port = 32772
              ), InternalRouteTarget(
                name = "52c84bbf28dcc96bd4c4167eeeb7ff0a69bfb6eb",
                port = 32772
              ), InternalRouteTarget(
                name = "5ccec1ae37f9c8f9e8eb1267bc176155541ceeb7",
                port = 32772
              )
            )
          ),
          DefaultRoute(
            name = "vamp/sava/port/_/vamp/sava/sava:1.1.0/port",
            metadata = Map(),
            path = GatewayPath("vamp/sava/sava:1.1.0/port"),
            selector = None,
            weight = Option(Percentage(10)),
            conditionStrength = None,
            condition = None,
            rewrites = Nil,
            balance = None,
            targets = List(
              InternalRouteTarget(
                name = "9019c00f1f7f641c4efc7a02c6f44e9f90d7750",
                port = 32773
              ), InternalRouteTarget(
                name = "49594c26c89754450bd4f562946a69070a4aa887",
                port = 32773
              )
            )
          )
        )
      ),
      Gateway(
        name = "vamp/port/_/_",
        metadata = Map(),
        port = Port("9050/http"),
        service = None,
        sticky = None,
        virtualHosts = Nil,
        selector = None,
        routes = DefaultRoute(
          name = "vamp/sava/port/_",
          metadata = Map(),
          path = GatewayPath("vamp/sava/port/_"),
          selector = None,
          weight = Option(Percentage(100)),
          conditionStrength = None,
          condition = None,
          rewrites = Nil,
          balance = None,
          targets = InternalRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            port = 33002
          ) :: Nil
        )
          :: Nil
      )
    ))

    compare(marshaller.marshall(HaProxy(converted.frontends, converted.backends, Nil, Nil), template), "configuration_8.txt")
  }

  it should "serialize A/B services to HAProxy configuration - sticky instance" in {
    val converted = marshaller.convert(List(
      Gateway(
        name = "vamp/sava/port/_",
        metadata = Map(),
        port = Port(33001),
        service = None,
        sticky = Some(Gateway.Sticky.Instance),
        virtualHosts = Nil,
        selector = None,
        routes = List(
          DefaultRoute(
            name = "vamp/sava/port/_/vamp/sava/sava:1.0.0/port",
            metadata = Map(),
            path = GatewayPath("vamp/sava/sava:1.0.0/port"),
            selector = None,
            weight = Option(Percentage(90)),
            conditionStrength = None,
            condition = None,
            rewrites = Nil,
            balance = None,
            targets = List(
              InternalRouteTarget(
                name = "64435a223bddf1fa589135baa5e228090279c032",
                port = 32772
              ), InternalRouteTarget(
                name = "52c84bbf28dcc96bd4c4167eeeb7ff0a69bfb6eb",
                port = 32772
              ), InternalRouteTarget(
                name = "5ccec1ae37f9c8f9e8eb1267bc176155541ceeb7",
                port = 32772
              )
            )
          ),
          DefaultRoute(
            name = "vamp/sava/port/_/vamp/sava/sava:1.1.0/port",
            metadata = Map(),
            path = GatewayPath("vamp/sava/sava:1.1.0/port"),
            selector = None,
            weight = Option(Percentage(10)),
            conditionStrength = None,
            condition = None,
            rewrites = Nil,
            balance = None,
            targets = List(
              InternalRouteTarget(
                name = "9019c00f1f7f641c4efc7a02c6f44e9f90d7750",
                port = 32773
              ), InternalRouteTarget(
                name = "49594c26c89754450bd4f562946a69070a4aa887",
                port = 32773
              )
            )
          )
        )
      ),
      Gateway(
        name = "vamp/port/_/_",
        metadata = Map(),
        port = Port("9050/http"),
        service = None,
        sticky = None,
        virtualHosts = Nil,
        selector = None,
        routes = DefaultRoute(
          name = "vamp/sava/port/_",
          metadata = Map(),
          path = GatewayPath("vamp/sava/port/_"),
          selector = None,
          weight = Option(Percentage(100)),
          conditionStrength = None,
          condition = None,
          rewrites = Nil,
          balance = None,
          targets = InternalRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            port = 33002
          ) :: Nil
        )
          :: Nil
      )
    ))

    compare(marshaller.marshall(HaProxy(converted.frontends, converted.backends, Nil, Nil), template), "configuration_9.txt")
  }

  it should "serialize A/B testing on deployments" in {
    val converted = marshaller.convert(List(
      Gateway(
        name = "vamp:1.x/sava/port",
        metadata = Map(),
        port = Port(33001),
        service = None,
        sticky = None,
        virtualHosts = Nil,
        selector = None,
        routes = DefaultRoute(
          name = "vamp:1.x/sava/sava:1.0.0/port",
          metadata = Map(),
          path = GatewayPath("vamp:1.x/sava/sava:1.0.0/port"),
          selector = None,
          weight = Option(Percentage(100)),
          conditionStrength = None,
          condition = None,
          rewrites = Nil,
          balance = None,
          targets = InternalRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            port = 32770
          ) :: Nil
        )
          :: Nil
      ),
      Gateway(
        name = "vamp:2.x/sava/port",
        metadata = Map(),
        port = Port(33001),
        service = None,
        sticky = None,
        virtualHosts = Nil,
        selector = None,
        routes = DefaultRoute(
          name = "vamp:2.x/sava/sava:2.0.0/port",
          metadata = Map(),
          path = GatewayPath("vamp:2.x/sava/sava:2.0.0/port"),
          selector = None,
          weight = Option(Percentage(100)),
          conditionStrength = None,
          condition = None,
          rewrites = Nil,
          balance = None,
          targets = InternalRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            port = 32771
          ) :: Nil
        )
          :: Nil
      ),
      Gateway(
        name = "vamp",
        metadata = Map(),
        port = Port("9050/http"),
        service = None,
        sticky = None,
        virtualHosts = Nil,
        selector = None,
        routes = List(
          DefaultRoute(
            name = "vamp:1.x/sava/port",
            metadata = Map(),
            path = GatewayPath("vamp:1.x/sava/port"),
            selector = None,
            weight = Option(Percentage(90)),
            conditionStrength = None,
            condition = None,
            rewrites = Nil,
            balance = None,
            targets = List(
              InternalRouteTarget(
                name = "64435a223bddf1fa589135baa5e228090279c032",
                port = 32772
              )
            )
          ),
          DefaultRoute(
            name = "vamp:2.x/sava/port",
            metadata = Map(),
            path = GatewayPath("vamp:2.x/sava/port"),
            selector = None,
            weight = Option(Percentage(10)),
            conditionStrength = None,
            condition = None,
            rewrites = Nil,
            balance = None,
            targets = List(
              InternalRouteTarget(
                name = "9019c00f1f7f641c4efc7a02c6f44e9f90d7750",
                port = 32773
              )
            )
          )
        )
      )
    ))

    compare(marshaller.marshall(HaProxy(converted.frontends, converted.backends, Nil, Nil), template), "configuration_10.txt")
  }

  it should "serialize custom balance" in {
    val converted = marshaller.convert(List(
      Gateway(
        name = "vamp:1.x/sava/port",
        metadata = Map(),
        port = Port(33001),
        service = None,
        sticky = None,
        virtualHosts = Nil,
        selector = None,
        routes = DefaultRoute(
          name = "vamp:1.x/sava/sava:1.0.0/port",
          metadata = Map(),
          path = GatewayPath("vamp:1.x/sava/sava:1.0.0/port"),
          selector = None,
          weight = Option(Percentage(100)),
          conditionStrength = None,
          condition = None,
          rewrites = Nil,
          balance = Some("first"),
          targets = InternalRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            port = 32770
          ) :: Nil
        )
          :: Nil
      ),
      Gateway(
        name = "vamp:2.x/sava/port",
        metadata = Map(),
        port = Port(33001),
        service = None,
        sticky = None,
        virtualHosts = Nil,
        selector = None,
        routes = DefaultRoute(
          name = "vamp:2.x/sava/sava:2.0.0/port",
          metadata = Map(),
          path = GatewayPath("vamp:2.x/sava/sava:2.0.0/port"),
          selector = None,
          weight = Option(Percentage(100)),
          conditionStrength = None,
          condition = None,
          rewrites = Nil,
          balance = Some("custom"),
          targets = InternalRouteTarget(
            name = "64435a223bddf1fa589135baa5e228090279c032",
            port = 32771
          ) :: Nil
        )
          :: Nil
      )
    ))

    compare(marshaller.marshall(HaProxy(converted.frontends, converted.backends, Nil, Nil), template), "configuration_11.txt")
  }

  it should "serialize single service http route with virtual hosts" in {
    val converted = marshaller.convert(Gateway(
      name = "deployment/cluster/port",
      metadata = Map(),
      port = Port(33000),
      service = None,
      sticky = None,
      virtualHosts = "port.cluster.deployment" :: Nil,
      selector = None,
      routes = DefaultRoute(
        name = "vamp/sava/port/_/vamp/sava/sava:1.0.0/port",
        metadata = Map(),
        path = GatewayPath("vamp/sava/sava:1.0.0/port"),
        selector = None,
        weight = Option(Percentage(100)),
        conditionStrength = None,
        condition = None,
        rewrites = Nil,
        balance = None,
        targets = InternalRouteTarget(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          port = 32768
        ) :: Nil
      ) :: Nil
    ))

    compare(marshaller.marshall(HaProxy(converted.frontends, converted.backends, converted.virtualHostFrontends, converted.virtualHostBackends), template), "configuration_12.txt")
  }

  it should "serialize single service http route with multiple virtual hosts" in {
    val converted = marshaller.convert(Gateway(
      name = "deployment/cluster/port",
      metadata = Map(),
      port = Port(33000),
      service = None,
      sticky = None,
      virtualHosts = "port.cluster.deployment" :: "a.b.c.d" :: "vamp.vamp" :: Nil,
      selector = None,
      routes = DefaultRoute(
        name = "vamp/sava/port/_/vamp/sava/sava:1.0.0/port",
        metadata = Map(),
        path = GatewayPath("vamp/sava/sava:1.0.0/port"),
        selector = None,
        weight = Option(Percentage(100)),
        conditionStrength = None,
        condition = None,
        rewrites = Nil,
        balance = None,
        targets = InternalRouteTarget(
          name = "64435a223bddf1fa589135baa5e228090279c032",
          port = 32768
        ) :: Nil
      ) :: Nil
    ))

    compare(marshaller.marshall(HaProxy(converted.frontends, converted.backends, converted.virtualHostFrontends, converted.virtualHostBackends), template), "configuration_13.txt")
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
