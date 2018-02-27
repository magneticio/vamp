package io.vamp.model.reader

import io.vamp.model.artifact._
import io.vamp.model.notification.{ RouteSelectorOnlyRouteError, _ }
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class GatewayReaderSpec extends FlatSpec with Matchers with ReaderSpec {

  "GatewayReader" should "read a gateway" in {
    GatewayReader.read(res("gateway/gateway1.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080/http"))),
      'sticky(Some(Gateway.Sticky.Route)),
      'routes(List(DefaultRoute("", Map(), GatewayPath("sava1", List("sava1")), None, Some(Percentage(50)), None, None, Nil, None), DefaultRoute("", Map(), GatewayPath("sava2/v1", List("sava2", "v1")), None, Some(Percentage(50)), None, None, Nil, None)))
    )
  }

  it should "read a deployment gateway" in {
    GatewayReader.read(res("gateway/gateway2.yml")) should have(
      'name("sava/web"),
      'port(Port("8080", None, Some("8080/tcp"))),
      'sticky(None),
      'routes(List(DefaultRoute("", Map(), GatewayPath("web/port", List("web", "port")), None, Some(Percentage(100)), None, None, Nil, None)))
    )
  }

  it should "fail on unsupported name format" in {
    expectedError[UnsupportedGatewayNameError]({
      GatewayReader.read(res("gateway/gateway3.yml"))
    }) should have(
      'name("a/b/c/d/e")
    )
  }

  it should "fail on sticky tcp port" in {
    expectedError[StickyPortTypeError]({
      GatewayReader.read(res("gateway/gateway4.yml"))
    }) should have(
      'port(Port("8080/tcp", None, Some("8080/tcp")))
    )
  }

  it should "read route balance" in {
    GatewayReader.read(res("gateway/gateway5.yml")) should have(
      'name("sava/web"),
      'port(Port("8080", None, Some("8080/tcp"))),
      'sticky(None),
      'routes(List(
        DefaultRoute("", Map(), GatewayPath("web/port1", List("web", "port1")), None, Some(Percentage(40)), None, None, Nil, Some("custom 1")),
        DefaultRoute("", Map(), GatewayPath("web/port2", List("web", "port2")), None, Some(Percentage(60)), None, None, Nil, Some("custom 2"))
      ))
    )
  }

  it should "read route rewrites" in {
    GatewayReader.read(res("gateway/gateway6.yml")) should have(
      'name("sava/web"),
      'port(Port("8080", None, Some("8080"))),
      'sticky(None),
      'routes(List(
        DefaultRoute("", Map(), GatewayPath("web/port1", List("web", "port1")), None, None, None, None, List(PathRewrite("", "a", "b")), None),
        DefaultRoute("", Map(), GatewayPath("web/port2", List("web", "port2")), None, Some(Percentage(100)), None, None, Nil, None)
      ))
    )
  }

  it should "parse external route" in {
    GatewayReader.read(res("gateway/gateway7.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080/http"))),
      'sticky(Some(Gateway.Sticky.Route)),
      'routes(List(DefaultRoute("", Map(), GatewayPath("[external/1/2]", List("[external/1/2]")), None, Some(Percentage(100)), None, None, Nil, None)))
    )
  }

  it should "parse virtual hosts" in {
    GatewayReader.read(res("gateway/gateway8.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080"))),
      'virtualHosts(List("a.b.c", "test")),
      'routes(List(DefaultRoute("", Map(), GatewayPath("[external/1/2]", List("[external/1/2]")), None, Some(Percentage(100)), None, None, Nil, None)))
    )
  }

  it should "parse empty virtual hosts" in {
    GatewayReader.read(res("gateway/gateway9.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080"))),
      'virtualHosts(Nil),
      'routes(List(DefaultRoute("", Map(), GatewayPath("[external/1/2]", List("[external/1/2]")), None, Some(Percentage(100)), None, None, Nil, None)))
    )
  }

  it should "expand virtual hosts" in {
    GatewayReader.read(res("gateway/gateway10.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080"))),
      'virtualHosts(List("inline")),
      'routes(List(DefaultRoute("", Map(), GatewayPath("[external/1/2]", List("[external/1/2]")), None, Some(Percentage(100)), None, None, Nil, None)))
    )
  }

  it should "fail on invalid host" in {
    expectedError[IllegalGatewayVirtualHosts.type]({
      GatewayReader.read(res("gateway/gateway11.yml"))
    })
  }

  it should "parse http service port" in {
    GatewayReader.read(res("gateway/gateway12.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080/http"))),
      'service(Option(GatewayService("127.0.0.1", Port("31234", None, Some("31234/http"))))),
      'sticky(Some(Gateway.Sticky.Route)),
      'routes(List(DefaultRoute("", Map(), GatewayPath("sava1", List("sava1")), None, Some(Percentage(50)), None, None, Nil, None), DefaultRoute("", Map(), GatewayPath("sava2/v1", List("sava2", "v1")), None, Some(Percentage(50)), None, None, Nil, None)))
    )
  }

  it should "parse tcp service port" in {
    GatewayReader.read(res("gateway/gateway13.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080/http"))),
      'service(Option(GatewayService("127.0.0.1", Port("31234", None, Some("31234/tcp"))))),
      'sticky(Some(Gateway.Sticky.Route)),
      'routes(List(DefaultRoute("", Map(), GatewayPath("sava1", List("sava1")), None, Some(Percentage(50)), None, None, Nil, None), DefaultRoute("", Map(), GatewayPath("sava2/v1", List("sava2", "v1")), None, Some(Percentage(50)), None, None, Nil, None)))
    )
  }

  it should "parse by default http service port" in {
    GatewayReader.read(res("gateway/gateway14.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080/http"))),
      'selector(None),
      'service(Option(GatewayService("127.0.0.1", Port("31234", None, Some("31234"))))),
      'sticky(Some(Gateway.Sticky.Route)),
      'routes(List(DefaultRoute("", Map(), GatewayPath("sava1", List("sava1")), None, Some(Percentage(50)), None, None, Nil, None), DefaultRoute("", Map(), GatewayPath("sava2/v1", List("sava2", "v1")), None, Some(Percentage(50)), None, None, Nil, None)))
    )
  }

  it should "fail on invalid total weight != 0 or != 100" in {
    expectedError[GatewayRouteWeightError]({
      GatewayReader.read(res("gateway/gateway15.yml"))
    })
  }

  it should "fail on multiple routes and invalid total weight != 0 or != 100" in {
    expectedError[GatewayRouteWeightError]({
      GatewayReader.read(res("gateway/gateway16.yml"))
    })
  }

  it should "parse empty selector" in {
    GatewayReader.read(res("gateway/gateway17.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080/http"))),
      'selector(None)
    )
  }

  it should "parse null selector" in {
    GatewayReader.read(res("gateway/gateway18.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080/http"))),
      'selector(None)
    )
  }

  it should "parse selector" in {
    GatewayReader.read(res("gateway/gateway19.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080/http"))),
      'selector(Option(RouteSelector("label(winter) and id(cold)")))
    )
  }

  it should "fail if selector and routes are defined" in {
    expectedError[RouteSelectorAndRoutesDefinedError.type]({
      GatewayReader.read(res("gateway/gateway20.yml"))
    })
  }

  it should "fail if selector is invalid" in {
    expectedError[InvalidRouteSelectorError]({
      GatewayReader.read(res("gateway/gateway21.yml"))
    })
  }

  it should "parse route null selector" in {
    GatewayReader.read(res("gateway/gateway22.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080/http"))),
      'routes(List(DefaultRoute("", Map(), GatewayPath("web", List("web")), None, None, None, None, Nil, None)))
    )
  }

  it should "parse route selector" in {
    GatewayReader.read(res("gateway/gateway23.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080/http"))),
      'routes(List(DefaultRoute("", Map(), GatewayPath("web", List("web")), Option(RouteSelector("image(winter)")), None, None, None, Nil, None)))
    )
  }

  it should "fail if route selector is invalid" in {
    expectedError[InvalidRouteSelectorError]({
      GatewayReader.read(res("gateway/gateway24.yml"))
    })
  }

  it should "fail if route selector is defined and path length != 1" in {
    expectedError[RouteSelectorOnlyRouteError.type]({
      GatewayReader.read(res("gateway/gateway25.yml"))
    })
  }
}
