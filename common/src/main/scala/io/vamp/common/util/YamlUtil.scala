package io.vamp.common.util

import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.error.YAMLException

import scala.collection.JavaConverters._
import scala.collection.mutable

object YamlUtil {

  def yaml: Yaml = {
    new Yaml(new Constructor() {
      override def getClassForName(name: String): Class[_] = throw new YAMLException("Not supported.")
    })
  }

  def convert(any: Any, preserveOrder: Boolean): Any = any match {
    case source: java.util.Map[_, _] ⇒
      if (preserveOrder) {
        val map = new mutable.LinkedHashMap[String, Any]()
        source.entrySet().asScala.foreach(entry ⇒ map += entry.getKey.toString → convert(entry.getValue, preserveOrder))
        map
      }
      else source.entrySet().asScala.map(entry ⇒ entry.getKey.toString → convert(entry.getValue, preserveOrder)).toMap
    case source: java.util.List[_]     ⇒ source.asScala.map(convert(_, preserveOrder)).toList
    case source: java.lang.Iterable[_] ⇒ source.asScala.map(convert(_, preserveOrder)).toList
    case source                        ⇒ source
  }
}
