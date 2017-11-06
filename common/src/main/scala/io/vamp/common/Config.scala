package io.vamp.common

import java.io.File

import _root_.akka.util.Timeout
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.{ ConfigFactory, ConfigValueFactory, Config ⇒ TypesafeConfig }
import io.vamp.common.util.{ ObjectUtil, YamlUtil }
import org.json4s.native.Serialization._
import org.json4s.{ DefaultFormats, Extraction, Formats }

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.implicitConversions

object Config {

  object Type extends Enumeration {
    val applied, dynamic, system, environment, application, reference = Value
  }

  private implicit val formats: Formats = DefaultFormats

  private val values: mutable.Map[String, mutable.Map[Type.Value, TypesafeConfig]] = new mutable.LinkedHashMap()

  def marshall(config: Map[String, Any]): String = if (config.isEmpty) "" else writePretty(config)

  def unmarshall(input: String, filter: ConfigFilter = ConfigFilter.acceptAll, flatten: Boolean = false): Map[String, Any] = {
    val yaml = Option(expand(YamlUtil.convert(YamlUtil.yaml.load(input), preserveOrder = false).asInstanceOf[Map[String, AnyRef]]))
    val json = yaml.map(write(_)).getOrElse("")
    val flat = convert(ConfigFactory.parseString(json))
    val filtered = flat.filter { case (key, value) ⇒ filter.filter(key, value) }
    if (flatten) filtered else expand(filtered)
  }

  def load(dynamic: Map[String, Any] = Map())(implicit namespace: Namespace): Unit = {
    val reference = convert(ConfigFactory.defaultReference())
    val application = convert(ConfigFactory.defaultApplication())
    val systemExpanded = expand(convert(ConfigFactory.systemProperties()))
    val dynamicExpanded = expand(dynamic.asInstanceOf[Map[String, AnyRef]])

    val environmentExpanded = expand((reference ++ application).flatMap {
      case (key, _) ⇒
        sys.env.get(key.replaceAll("[^\\p{L}\\d]", "_").toUpperCase).map { value ⇒
          key → ConfigValueFactory.fromAnyRef(value.trim).unwrapped
        }
    })

    val referenceExpanded = expand(reference)
    val applicationExpanded = expand(application)

    val applied = ObjectUtil.merge(
      referenceExpanded,
      applicationExpanded,
      environmentExpanded,
      systemExpanded,
      dynamicExpanded
    )

    val appliedWitNamespace = expand(flatten(applied) ++ flatten(expand(namespace.config.asInstanceOf[Map[String, AnyRef]])))

    values.getOrElseUpdate(namespace.name, new mutable.LinkedHashMap()).put(Type.system, convert(systemExpanded))
    values.getOrElseUpdate(namespace.name, new mutable.LinkedHashMap()).put(Type.dynamic, convert(dynamicExpanded))
    values.getOrElseUpdate(namespace.name, new mutable.LinkedHashMap()).put(Type.reference, convert(referenceExpanded))
    values.getOrElseUpdate(namespace.name, new mutable.LinkedHashMap()).put(Type.application, convert(applicationExpanded))
    values.getOrElseUpdate(namespace.name, new mutable.LinkedHashMap()).put(Type.environment, convert(environmentExpanded))
    values.getOrElseUpdate(namespace.name, new mutable.LinkedHashMap()).put(Type.applied, convert(appliedWitNamespace))
  }

  def parse(file: File, expanded: Boolean = true): Map[String, Any] = {
    val config = convert(ConfigFactory.parseFile(file))
    if (expanded) expand(config) else config
  }

  def int(path: String): ConfigMagnet[Int] = get(path, {
    _.getInt(path)
  })

  def double(path: String): ConfigMagnet[Double] = get(path, {
    _.getDouble(path)
  })

  def string(path: String): ConfigMagnet[String] = get(path, {
    _.getString(path)
  })

  def boolean(path: String): ConfigMagnet[Boolean] = get(path, {
    _.getBoolean(path)
  })

  def intList(path: String): ConfigMagnet[List[Int]] = get(path, {
    _.getIntList(path).asScala.map(_.toInt).toList
  })

  def stringList(path: String): ConfigMagnet[List[String]] = get(path, {
    _.getStringList(path).asScala.toList
  })

  def duration(path: String): ConfigMagnet[FiniteDuration] = get(path, {
    config ⇒ FiniteDuration(config.getDuration(path, MILLISECONDS), MILLISECONDS)
  })

  def timeout(path: String): ConfigMagnet[Timeout] = get(path, {
    config ⇒ Timeout(FiniteDuration(config.getDuration(path, MILLISECONDS), MILLISECONDS))
  })

  def has(path: String)(implicit namespace: Namespace): () ⇒ Boolean = () ⇒ {
    values.get(namespace.name).flatMap(_.get(Type.applied)).exists(_.hasPath(path))
  }

  def list(path: String): ConfigMagnet[List[AnyRef]] = get(path, { config ⇒
    config.getList(path).unwrapped.asScala.map(ObjectUtil.asScala).toList
  })

  def entries(path: String = ""): ConfigMagnet[Map[String, AnyRef]] = get(path, { config ⇒
    val cfg = if (path.nonEmpty) config.getConfig(path) else config
    cfg.entrySet.asScala.map { entry ⇒ entry.getKey → ObjectUtil.asScala(cfg.getAnyRef(entry.getKey)) }.toMap
  })

  def export(`type`: Config.Type.Value, flatten: Boolean = true, filter: ConfigFilter = ConfigFilter.acceptAll)(implicit namespace: Namespace): Map[String, Any] = {
    val entries = convert(values.get(namespace.name).flatMap(_.get(`type`)).getOrElse(ConfigFactory.empty())).filter { case (key, value) ⇒ filter.filter(key, value) }
    if (flatten) entries else expand(entries)
  }

  private def get[T](path: String, process: TypesafeConfig ⇒ T): ConfigMagnet[T] = new ConfigMagnet[T] {
    def apply()(implicit namespace: Namespace): T = {
      values.getOrElseUpdate(namespace.name, new mutable.LinkedHashMap()).get(Type.applied) match {
        case Some(applied) ⇒ process(applied)
        case _ ⇒ {
          values.getOrElseUpdate(namespace.name, new mutable.LinkedHashMap()).get(Type.environment) match {
            case Some(applied) ⇒ process(applied)
            case _             ⇒ throw new Missing(path)
          }
        }
      }
    }
  }

  private def convert(config: TypesafeConfig): Map[String, AnyRef] = {
    config.resolve().entrySet().asScala.map { entry ⇒
      entry.getKey → ObjectUtil.asScala(entry.getValue.unwrapped)
    }.toMap
  }

  private def convert(config: Map[String, AnyRef]): TypesafeConfig = ConfigFactory.parseString(write(expand(config)))

  private def flatten(config: Map[String, AnyRef]): Map[String, AnyRef] = convert(convert(config))

  private def expand[T](any: T): T = {
    def split(key: Any, value: Any) = {
      // preferable com.typesafe.config.impl.PathParser.parsePath(key.toString), but it has no public access
      key.toString.trim.split('.').foldRight[AnyRef](value.asInstanceOf[AnyRef])((op1, op2) ⇒ Map(op1 → op2))
    }

    any match {
      case entries: Map[_, _] ⇒
        entries.map {
          case (key, value) ⇒
            value match {
              case v: Seq[_]            ⇒ split(key, v.map(expand))
              case v: Iterator[_]       ⇒ split(key, v.map(expand))
              case v: Map[_, _]         ⇒ split(key, expand(v.asInstanceOf[Map[String, AnyRef]]))
              case v: mutable.Map[_, _] ⇒ split(key, expand(v.asInstanceOf[Map[String, AnyRef]]))
              case other                ⇒ split(key, other)
            }
        }.foldLeft(Extraction.decompose(Map())) {
          (op1, op2) ⇒ op1 merge Extraction.decompose(op2)
        }.extract[Map[String, AnyRef]].asInstanceOf[T]

      case list: List[_] ⇒ list.map(expand).asInstanceOf[T]
      case other         ⇒ other
    }
  }
}

trait ConfigMagnet[T] {
  def apply()(implicit namespace: Namespace): T
}

object ConfigFilter {

  val acceptAll = ConfigFilter((_, _) ⇒ true)

  implicit def function2filter(filter: (String, AnyRef) ⇒ Boolean): ConfigFilter = ConfigFilter(filter)
}

case class ConfigFilter(filter: (String, AnyRef) ⇒ Boolean)
