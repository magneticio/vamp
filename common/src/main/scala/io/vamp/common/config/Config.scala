package io.vamp.common.config

import akka.util.Timeout
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.{ ConfigFactory, ConfigValue, ConfigValueFactory, Config ⇒ TypesafeConfig }
import org.json4s.{ DefaultFormats, Extraction, Formats }

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Try

object Config {

  object Type extends Enumeration {
    val applied, dynamic, system, environment, application, reference = Value
  }

  private val values: mutable.Map[Type.Value, Map[String, ConfigValue]] = new mutable.LinkedHashMap()

  protected var applied: Option[TypesafeConfig] = None

  def load(config: Map[String, Any] = Map()): Unit = {

    val dynamic = config.map {
      case (key, value) ⇒ key → ConfigValueFactory.fromAnyRef(value)
    }

    val system = convert(ConfigFactory.systemProperties())
    val application = convert(ConfigFactory.defaultApplication())
    val reference = convert(ConfigFactory.defaultReference())

    val environment = (reference ++ application ++ system).flatMap {
      case (key, _) ⇒ environmentValue(key).map(value ⇒ key → ConfigValueFactory.fromAnyRef(value))
    }

    val applied = reference ++ application ++ system ++ environment ++ dynamic

    values.put(Type.applied, applied)
    values.put(Type.system, system)
    values.put(Type.dynamic, dynamic)
    values.put(Type.reference, reference)
    values.put(Type.application, application)
    values.put(Type.environment, environment)

    this.applied = Option(ConfigFactory.parseMap(applied.map {
      case (key, value) ⇒ key → value.unwrapped()
    }.asJava))
  }

  def values(`type`: Type.Value): Map[String, AnyRef] = values.getOrElse(`type`, Map())

  def int(path: String) = get(path, { _.getInt(path) })

  def double(path: String) = get(path, { _.getDouble(path) })

  def string(path: String) = get(path, { _.getString(path) })

  def boolean(path: String) = get(path, { _.getBoolean(path) })

  def intList(path: String) = get(path, { _.getIntList(path).asScala.map(_.toInt).toList })

  def stringList(path: String) = get(path, { _.getStringList(path).asScala.toList })

  def duration(path: String) = get(path, { config ⇒ FiniteDuration(config.getDuration(path, MILLISECONDS), MILLISECONDS) })

  def timeout(path: String) = () ⇒ Timeout(duration(path)())

  def has(path: String) = () ⇒ Try(applied.get.hasPath(path)).getOrElse(false)

  def entries(path: String = ""): () ⇒ Map[String, AnyRef] = get(path, { config ⇒
    val cfg = if (path.nonEmpty) config.getConfig(path) else config
    cfg.entrySet.asScala.map(entry ⇒ entry.getKey → cfg.getAnyRef(entry.getKey)).toMap
  })

  def export(`type`: Config.Type.Value, flatten: Boolean, filter: String ⇒ Boolean) = {
    val entries = values.getOrElse(`type`, Map()).filter { case (key, _) ⇒ filter(key) }
    if (flatten) entries
    else {
      implicit val formats: Formats = DefaultFormats
      entries.map {
        case (key, value) ⇒ key.split('.').foldRight[AnyRef](value)((op1, op2) ⇒ Map(op1 → op2))
      }.foldLeft(Extraction.decompose(Map()))((op1, op2) ⇒ op1 merge Extraction.decompose(op2)).extract[Map[String, AnyRef]]
    }
  }

  private def get[T](path: String, process: TypesafeConfig ⇒ T): () ⇒ T = {
    () ⇒ if (applied.isEmpty) throw new Missing(path) else process(applied.get)
  }

  private def convert(config: TypesafeConfig): Map[String, ConfigValue] = {
    config.resolve().entrySet().asScala.map(entry ⇒ entry.getKey → entry.getValue).toMap
  }

  private def environmentValue(path: String): Option[String] = {
    sys.env.get(path.replaceAll("[^\\p{L}\\d]", "_").toUpperCase).map(_.trim).map {
      value ⇒ if (value.startsWith("[") && value.endsWith("]")) value else s""""$value""""
    }
  }
}
