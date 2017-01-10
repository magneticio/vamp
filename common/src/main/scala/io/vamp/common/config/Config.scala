package io.vamp.common.config

import akka.util.Timeout
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.{ ConfigFactory, ConfigValue, ConfigValueFactory, Config ⇒ TypesafeConfig }
import io.vamp.common.util.YamlUtil
import org.json4s.native.Serialization.writePretty
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

  def load(input: String, validateOnly: Boolean): Unit = {
    implicit val formats: Formats = DefaultFormats

    def flatten(any: Any): Any = any match {
      case m: Map[_, _] ⇒
        m.collect {
          case (key, value) if key.isInstanceOf[String] ⇒
            val keys = key.asInstanceOf[String].split('.').toList.reverse
            keys.tail.foldLeft[Map[String, _]](Map(keys.head → flatten(value)))((op1, op2) ⇒ Map(op2 → op1))
        }.foldLeft(Extraction.decompose(Map())) {
          (op1, op2) ⇒ op1 merge Extraction.decompose(op2)
        }.extract[Map[String, AnyRef]]

      case l: List[_] ⇒ l.map(flatten)
      case other      ⇒ other
    }

    val yaml = flatten(YamlUtil.convert(YamlUtil.yaml.load(input), preserveOrder = false))
    val json = writePretty(yaml.asInstanceOf[AnyRef])
    val config = ConfigFactory.parseString(json)
    val entries = config.entrySet.asScala.map(entry ⇒ entry.getKey → config.getAnyRef(entry.getKey)).toMap

    if (!validateOnly) load(entries)
  }

  def load(config: Map[String, Any] = Map()): Unit = {

    def convert(config: TypesafeConfig): Map[String, ConfigValue] = {
      config.resolve().entrySet().asScala.map(entry ⇒ entry.getKey → entry.getValue).toMap
    }

    val dynamic = config.map {
      case (key, value) ⇒ key → ConfigValueFactory.fromAnyRef(value)
    }

    val system = convert(ConfigFactory.systemProperties())
    val application = convert(ConfigFactory.defaultApplication())
    val reference = convert(ConfigFactory.defaultReference())

    val environment = (reference ++ application ++ system).flatMap {
      case (key, _) ⇒ sys.env.get(key.replaceAll("[^\\p{L}\\d]", "_").toUpperCase).map { value ⇒
        key → ConfigValueFactory.fromAnyRef(value.trim)
      }
    }

    val applied = reference ++ application ++ system ++ environment ++ dynamic

    values.put(Type.applied, applied)
    values.put(Type.system, system)
    values.put(Type.dynamic, dynamic)
    values.put(Type.reference, reference)
    values.put(Type.application, application)
    values.put(Type.environment, environment)

    this.applied = Option(ConfigFactory.parseMap(applied.map {
      case (key, value) ⇒ key → value.unwrapped
    }.asJava))
  }

  def values(`type`: Type.Value): Map[String, AnyRef] = values.getOrElse(`type`, Map())

  def int(path: String) = get(path, {
    _.getInt(path)
  })

  def double(path: String) = get(path, {
    _.getDouble(path)
  })

  def string(path: String) = get(path, {
    _.getString(path)
  })

  def boolean(path: String) = get(path, {
    _.getBoolean(path)
  })

  def intList(path: String) = get(path, {
    _.getIntList(path).asScala.map(_.toInt).toList
  })

  def stringList(path: String) = get(path, {
    _.getStringList(path).asScala.toList
  })

  def duration(path: String) = get(path, { config ⇒ FiniteDuration(config.getDuration(path, MILLISECONDS), MILLISECONDS) })

  def timeout(path: String) = () ⇒ Timeout(duration(path)())

  def has(path: String) = () ⇒ Try(applied.get.hasPath(path)).getOrElse(false)

  def entries(path: String = ""): () ⇒ Map[String, AnyRef] = get(path, { config ⇒
    val cfg = if (path.nonEmpty) config.getConfig(path) else config
    cfg.entrySet.asScala.map(entry ⇒ entry.getKey → cfg.getAnyRef(entry.getKey)).toMap
  })

  def export(`type`: Config.Type.Value, flatten: Boolean, filter: String ⇒ Boolean) = {
    val entries = values.getOrElse(`type`, Map()).collect { case (key, value) if filter(key) ⇒ key → value.unwrapped }
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
}
