package io.vamp.gateway_driver.haproxy

import io.vamp.common.util.ObjectUtil._
import org.jtwig.{ JtwigModel, JtwigTemplate }

trait JTwigHaProxyGatewayMarshaller extends HaProxyGatewayMarshaller {

  def templateFile: String = ""

  override lazy val info: AnyRef = Map(
    "HAProxy" -> s"$version.x",
    "template" -> (if (templateFile.isEmpty) "default" else templateFile)
  )

  override private[haproxy] def marshall(haProxy: HaProxy): String = {

    val model = JtwigModel.newModel().`with`("haproxy", (unwrap andThen java)(haProxy))

    val template = {
      if (templateFile.isEmpty)
        JtwigTemplate.classpathTemplate("/io/vamp/gateway_driver/haproxy/template.twig")
      else
        JtwigTemplate.fileTemplate(templateFile)
    }

    template.render(model)
  }
}
