package io.vamp.model.reader

import io.vamp.model.artifact.{ DefaultRoute, GatewayPath, Gateway, Port }
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
      'port(Port("", None, Some("8080/http"))),
      'sticky(Some(Gateway.Sticky.Service)),
      'routes(List(DefaultRoute("", GatewayPath("sava1", List("sava1")), Some(Percentage(50)), Nil), DefaultRoute("", GatewayPath("sava2/v1", List("sava2", "v1")), Some(Percentage(50)), Nil)))
    )
  }

  it should "read a deployment gateway" in {
    GatewayReader.read(res("gateway/gateway2.yml")) should have(
      'name("sava/web"),
      'port(Port("", None, Some("8080/tcp"))),
      'sticky(None),
      'routes(List(DefaultRoute("", GatewayPath("web/port", List("web", "port")), Some(Percentage(100)), Nil)))
    )
  }

  it should "fail on unsupported name format" in {
    expectedError[UnsupportedGatewayNameError]({
      GatewayReader.read(res("gateway/gateway3.yml"))
    }) should have(
      'name("sava/web/port")
    )
  }

  it should "fail on sticky tcp port" in {
    expectedError[StickyPortTypeError]({
      GatewayReader.read(res("gateway/gateway4.yml"))
    }) should have(
      'port(Port("8080/tcp", None, Some("8080/tcp")))
    )
  }
}
