package io.vamp.gateway_driver.haproxy

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class JTwigHaProxyGatewayMarshallerSpec extends HaProxyGatewayMarshallerSpec with JTwigHaProxyGatewayMarshaller {
  override lazy val info = super[FlatSpec].info
}
