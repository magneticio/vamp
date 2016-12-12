package io.vamp.common.spi

import java.util.ServiceLoader

import scala.collection.JavaConverters.asScalaIteratorConverter

trait ServiceMapper[T] {

  def name: String

  def clazz: Class[_ <: T]
}

object ServiceProvider {

  def get[T](name: String, `type`: Class[_ <: ServiceMapper[T]]): Option[Class[T]] = {
    ServiceLoader.load(`type`).iterator.asScala.find {
      _.name == name
    } map {
      _.clazz.asInstanceOf[Class[T]]
    }
  }
}
