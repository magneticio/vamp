package io.vamp.common.config

import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._

import scala.collection.JavaConverters._
import scala.language.postfixOps

object Config extends Config(ConfigFactory.load())

private[config] class Config(config: com.typesafe.config.Config) {

  def int(path: String) = config.as[Int](path)

  def double(path: String) = config.as[Double](path)

  def string(path: String) = config.as[String](path)

  def boolean(path: String) = config.as[Boolean](path)

  def intList(path: String) = config.as[List[Int]](path)

  def stringList(path: String) = config.as[List[String]](path)

  def entries(path: String = ""): Map[String, AnyRef] = {
    config.getConfig(path).entrySet().asScala.map { entry ⇒ entry.getKey -> entry.getValue.unwrapped } toMap
  }

  def config(path: String): Config = new Config(config.getConfig(path))
}