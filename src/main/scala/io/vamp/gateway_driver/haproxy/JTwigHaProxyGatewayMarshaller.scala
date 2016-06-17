package io.vamp.gateway_driver.haproxy

import io.vamp.common.util.ObjectUtil._
import org.jtwig.{ JtwigModel, JtwigTemplate }

trait JTwigHaProxyGatewayMarshaller extends HaProxyGatewayMarshaller {

  override private[haproxy] def marshall(haProxy: HaProxy): String = {

    val model = JtwigModel.newModel().`with`("haproxy", (unwrap andThen java)(haProxy))
    val template = JtwigTemplate.classpathTemplate("/io/vamp/gateway_driver/haproxy/template.twig")

    template.render(model)
  }
}
