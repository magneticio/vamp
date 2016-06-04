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

    def read(entity: JsValue): DefaultScale = {
      entity.asJsObject().getFields("cpu", "instances", "memory", "name") match {
        case Seq(JsNumber(cpu), JsNumber(instances), JsNumber(memory), JsString(name)) ⇒ DefaultScale(name, Quantity(cpu.toDouble), MegaByte(memory.toDouble), instances.toInt)
      }
    }

    def write(entity: DefaultScale): JsValue = {
      JsObject("cpu" -> JsNumber(entity.cpu.value), "instances" -> JsNumber(entity.instances), "memory" -> JsNumber(entity.memory.value), "name" -> JsString(entity.name))
    }
  }
}