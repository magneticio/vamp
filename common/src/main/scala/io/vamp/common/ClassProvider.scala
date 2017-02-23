package io.vamp.common

import java.util.ServiceLoader

import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.reflect.{ ClassTag, classTag }

trait ClassMapper {

  def name: String

  def clazz: Class[_]
}

object ClassProvider {

  def all[T: ClassTag]: Iterator[T] = {
    ServiceLoader.load(classTag[T].runtimeClass).iterator.asScala.map(_.asInstanceOf[T])
  }

  def find[T: ClassTag](name: String): Option[Class[T]] = all[ClassMapper].collectFirst {
    case c if c.name == name && classTag[T].runtimeClass.isAssignableFrom(c.clazz) ⇒ c.clazz.asInstanceOf[Class[T]]
  }
}
