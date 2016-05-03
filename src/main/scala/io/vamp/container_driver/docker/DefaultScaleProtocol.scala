package io.vamp.container_driver.docker

import spray.json.DefaultJsonProtocol
import spray.json._
import io.vamp.model.reader.{ MegaByte, Quantity }
import io.vamp.model.artifact.DefaultScale

/**
 * @author Emiliano Martínez
 * DefaultScala spray-based serializer to store DefaultScale parameters
 */
object DefaultScaleProtocol extends DefaultJsonProtocol {

  implicit object DefaultScaleFormat extends RootJsonFormat[DefaultScale] {
    def read(e: JsValue): DefaultScale = {
      e.asJsObject().getFields("cpu", "instances", "memory", "name") match {
        case Seq(JsNumber(cpu), JsNumber(instances), JsNumber(memory), JsString(name)) ⇒ DefaultScale(name, Quantity(cpu.toDouble), MegaByte(memory.toDouble), instances.toInt)
      }
    }

    def write(e: DefaultScale): JsValue = {
      JsObject("cpu" -> JsNumber(e.cpu.value), "instances" -> JsNumber(e.instances), "memory" -> JsNumber(e.memory.value), "name" -> JsString(e.name))
    }
  }

}