package io.vamp.model.reader

import io.vamp.model.artifact._
import io.vamp.model.notification._
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class GatewayReaderTest extends FlatSpec with Matchers with ReaderTest {

  "GatewayReader" should "read a gateway" in {
    GatewayReader.read(res("gateway/gateway1.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080/http"))),
      'sticky(Some(Gateway.Sticky.Route)),
      'routes(List(DefaultRoute("", GatewayPath("sava1", List("sava1")), Some(Percentage(50)), None, Nil, Nil, None), DefaultRoute("", GatewayPath("sava2/v1", List("sava2", "v1")), Some(Percentage(50)), None, Nil, Nil, None)))
    )
  }

  it should "read a deployment gateway" in {
    GatewayReader.read(res("gateway/gateway2.yml")) should have(
      'name("sava/web"),
      'port(Port("8080", None, Some("8080/tcp"))),
      'sticky(None),
      'routes(List(DefaultRoute("", GatewayPath("web/port", List("web", "port")), Some(Percentage(100)), None, Nil, Nil, None)))
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
        DefaultRoute("", GatewayPath("web/port1", List("web", "port1")), Some(Percentage(40)), None, Nil, Nil, Some("custom 1")),
        DefaultRoute("", GatewayPath("web/port2", List("web", "port2")), Some(Percentage(60)), None, Nil, Nil, Some("custom 2"))
      ))
    )
  }

  it should "read route rewrites" in {
    GatewayReader.read(res("gateway/gateway6.yml")) should have(
      'name("sava/web"),
      'port(Port("8080", None, Some("8080"))),
      'sticky(None),
      'routes(List(
        DefaultRoute("", GatewayPath("web/port1", List("web", "port1")), None, None, Nil, List(PathRewrite("", "a", "b")), None),
        DefaultRoute("", GatewayPath("web/port2", List("web", "port2")), Some(Percentage(100)), None, Nil, Nil, None)
      ))
    )
  }

  it should "parse external route" in {
    GatewayReader.read(res("gateway/gateway7.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080/http"))),
      'sticky(Some(Gateway.Sticky.Route)),
      'routes(List(DefaultRoute("", GatewayPath("[external/1/2]", List("[external/1/2]")), Some(Percentage(100)), None, Nil, Nil, None)))
    )
  }

  it should "parse virtual hosts" in {
    GatewayReader.read(res("gateway/gateway8.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080"))),
      'virtualHosts(List("a.b.c", "test")),
      'routes(List(DefaultRoute("", GatewayPath("[external/1/2]", List("[external/1/2]")), Some(Percentage(100)), None, Nil, Nil, None)))
    )
  }

  it should "parse empty virtual hosts" in {
    GatewayReader.read(res("gateway/gateway9.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080"))),
      'virtualHosts(Nil),
      'routes(List(DefaultRoute("", GatewayPath("[external/1/2]", List("[external/1/2]")), Some(Percentage(100)), None, Nil, Nil, None)))
    )
  }

  it should "expand virtual hosts" in {
    GatewayReader.read(res("gateway/gateway10.yml")) should have(
      'name("sava"),
      'port(Port("8080", None, Some("8080"))),
      'virtualHosts(List("inline")),
      'routes(List(DefaultRoute("", GatewayPath("[external/1/2]", List("[external/1/2]")), Some(Percentage(100)), None, Nil, Nil, None)))
    )
  }

  it should "fail on invalid host" in {
    expectedError[IllegalGatewayVirtualHosts.type]({
      GatewayReader.read(res("gateway/gateway11.yml"))
    })
  }
}
