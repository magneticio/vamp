package io.vamp.common.config

import akka.util.Timeout
import com.typesafe.config.{ ConfigFactory, Config ⇒ TypesafeConfig }

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.language.postfixOps

object Config extends Config(ConfigFactory.load(), "")

private[config] class Config(config: TypesafeConfig, root: String) {

  def int(path: String) = read(path).getInt(path)

  def double(path: String) = read(path).getDouble(path)

  def string(path: String) = read(path).getString(path)

  def boolean(path: String) = read(path).getBoolean(path)

  def intList(path: String) = read(path).getIntList(path).asScala.toList

  def stringList(path: String) = read(path).getStringList(path).asScala.toList

  def timeout(path: String) = Timeout(duration(path))

  def duration(path: String) = FiniteDuration(read(path).getDuration(path, MILLISECONDS), MILLISECONDS)

  def config(path: String): Config = new Config(config.getConfig(path), absolutePath(path))

  def entries(path: String = ""): Map[String, AnyRef] = {

    val cfg = if (path.nonEmpty) config.getConfig(path) else config

    cfg.entrySet.asScala.map { entry ⇒

      val key = entry.getKey

      val value = environment(absolutePath(path, key)).map {
        value ⇒ ConfigFactory.parseString(s"$key:$value").withFallback(config)
      } getOrElse cfg getAnyRef key

      key -> value

    } toMap
  }

  private def read(path: String): TypesafeConfig = {
    environment(absolutePath(path)).map { value ⇒
      if (value.startsWith("[") && value.endsWith("]")) value else s""""$value""""
    } map { value ⇒
      ConfigFactory.parseString(s"$path = $value").withFallback(config)
    } getOrElse config
  }

  private def environment(path: String): Option[String] = {
    sys.env.get(path.replaceAll("[^\\p{L}\\d]", "_").toUpperCase).map(_.trim)
  }

  private def mergePaths(paths: String*): String = {
    paths.toList.filter(_.nonEmpty).mkString(".")
  }

  private def absolutePath(paths: String*): String = {
    mergePaths(root :: paths.toList: _*)
  }
}
