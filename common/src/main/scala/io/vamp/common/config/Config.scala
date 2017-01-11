package io.vamp.common.config

import akka.util.Timeout
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.{ ConfigFactory, ConfigValueFactory, Config ⇒ TypesafeConfig }
import io.vamp.common.util.{ ObjectUtil, YamlUtil }
import org.json4s.native.Serialization.writePretty
import org.json4s.{ DefaultFormats, Extraction, Formats }

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration._

object Config {

  object Type extends Enumeration {
    val applied, dynamic, system, environment, application, reference = Value
  }

  private implicit val formats: Formats = DefaultFormats

  private val values: mutable.Map[Type.Value, TypesafeConfig] = new mutable.LinkedHashMap()

  def marshall(config: Map[String, Any]): String = if (config.isEmpty) "" else writePretty(config)

  def unmarshall(input: String): Map[String, Any] = {

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

    config.entrySet.asScala.map(entry ⇒ entry.getKey → config.getAnyRef(entry.getKey)).toMap
  }

  def load(config: Map[String, Any] = Map()): Unit = {

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

    values.put(Type.system, convert(system))
    values.put(Type.dynamic, convert(dynamic))
    values.put(Type.reference, convert(reference))
    values.put(Type.application, convert(application))
    values.put(Type.environment, convert(environment))
    values.put(Type.applied, convert(reference ++ application ++ system ++ environment ++ dynamic))
  }

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

  def has(path: String): () ⇒ Boolean = () ⇒ values.get(Type.applied).exists(_.hasPath(path))

  def list(path: String): () ⇒ List[AnyRef] = get(path, { config ⇒
    config.getList(path).unwrapped.asScala.map(ObjectUtil.scalaAnyRef).toList
  })

  def entries(path: String = ""): () ⇒ Map[String, AnyRef] = get(path, { config ⇒
    val cfg = if (path.nonEmpty) config.getConfig(path) else config
    cfg.entrySet.asScala.map { entry ⇒ entry.getKey → ObjectUtil.scalaAnyRef(cfg.getAnyRef(entry.getKey)) }.toMap
  })

  def export(`type`: Config.Type.Value, flatten: Boolean = true, filter: String ⇒ Boolean = { _ ⇒ true }): Map[String, Any] = {
    val entries = convert(values.getOrElse(`type`, ConfigFactory.empty())).filter { case (key, _) ⇒ filter(key) }
    if (flatten) entries
    else {
      implicit val formats: Formats = DefaultFormats
      entries.map {
        case (key, value) ⇒ key.split('.').foldRight[AnyRef](value)((op1, op2) ⇒ Map(op1 → op2))
      }.foldLeft(Extraction.decompose(Map()))((op1, op2) ⇒ op1 merge Extraction.decompose(op2)).extract[Map[String, AnyRef]]
    }
  }

  private def get[T](path: String, process: TypesafeConfig ⇒ T): () ⇒ T = { () ⇒
    values.get(Type.applied) match {
      case Some(applied) ⇒ process(applied)
      case _             ⇒ throw new Missing(path)
    }
  }

  private def convert(config: TypesafeConfig): Map[String, AnyRef] = {
    config.resolve().entrySet().asScala.map(entry ⇒ entry.getKey → entry.getValue.unwrapped).toMap
  }

  private def convert(config: Map[String, AnyRef]): TypesafeConfig = ConfigFactory.parseMap(config.asJava)
}
