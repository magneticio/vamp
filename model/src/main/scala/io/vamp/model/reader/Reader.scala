package io.vamp.model.reader

import java.io.{ File, InputStream, Reader, StringReader }

import io.vamp.common.notification.NotificationErrorException
import io.vamp.model.artifact._
import io.vamp.model.notification._
import io.vamp.model.reader.YamlSourceReader._
import io.vamp.model.resolver.TraitResolver
import org.json4s.native.Serialization
import org.json4s.{ DefaultFormats, Formats }
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.error.YAMLException

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Source
import scala.language.{ implicitConversions, postfixOps }
import scala.reflect._

sealed trait YamlSource

case class StringSource(string: String) extends YamlSource

case class ReaderSource(reader: Reader) extends YamlSource

case class StreamSource(stream: InputStream) extends YamlSource

case class FileSource(file: File) extends YamlSource

object YamlSource {
  implicit def string2YamlInput(string: String): YamlSource = StringSource(string)

  implicit def reader2YamlInput(reader: Reader): YamlSource = ReaderSource(reader)

  implicit def stream2YamlInput(stream: InputStream): YamlSource = StreamSource(stream)

  implicit def file2YamlInput(file: File): YamlSource = FileSource(file)
}

trait YamlReader[T] extends ModelNotificationProvider {

  def read(input: YamlSource): T = input match {
    case ReaderSource(reader) ⇒ read(reader)
    case StreamSource(stream) ⇒ read(Source.fromInputStream(stream).bufferedReader())
    case StringSource(string) ⇒ read(new StringReader(string))
    case FileSource(file)     ⇒ read(Source.fromFile(file).bufferedReader())
  }

  private def read(reader: Reader): T = load(reader, {
    case yaml: YamlSourceReader ⇒ read(yaml)
    case any                    ⇒ throwException(UnexpectedTypeError("/", classOf[YamlSourceReader], if (any != null) any.getClass else classOf[Object]))
  })

  protected def load(reader: Reader, process: PartialFunction[Any, T]): T = {
    def convert(any: Any): Any = any match {
      case source: java.util.Map[_, _] ⇒
        // keeping the order
        val map = new mutable.LinkedHashMap[String, Any]()
        source.entrySet().asScala.foreach(entry ⇒ map += entry.getKey.toString -> convert(entry.getValue))
        map
      case source: java.util.List[_] ⇒ source.asScala.map(convert).toList
      case source                    ⇒ source
    }

    try {

      val source = convert(yaml.load(reader)) match {
        case map: collection.Map[_, _] ⇒ YamlSourceReader(map.toMap.asInstanceOf[Map[String, _]])
        case any                       ⇒ any
      }

      val result = process(source)

      source match {
        case yaml: YamlSourceReader ⇒
          val nonConsumed = yaml.notConsumed
          if (nonConsumed.nonEmpty) {
            implicit val formats: Formats = DefaultFormats
            throwException(UnexpectedElement(nonConsumed, Serialization.write(nonConsumed)))
          }
        case _ ⇒
      }

      result

    } catch {
      case e: NotificationErrorException ⇒ throw e
      case e: YAMLException              ⇒ throwException(YamlParsingError(e.getMessage.replaceAll("java object", "resource"), e))
    } finally {
      reader.close()
    }
  }

  protected def yaml = {
    new Yaml(new Constructor() {
      override def getClassForName(name: String): Class[_] = throw new YAMLException("Not supported.")
    })
  }

  def read(implicit source: YamlSourceReader): T = {
    val expanded = expand(source)
    validate(expanded)
    val parsed = parse(expanded)
    validate(parsed)
  }

  protected def expand(implicit source: YamlSourceReader): YamlSourceReader = source

  protected def validate(implicit source: YamlSourceReader): YamlSourceReader = source

  protected def parse(implicit source: YamlSourceReader): T

  protected def validate(any: T): T = any

  protected def <<![V <: Any: ClassTag](path: YamlPath)(implicit source: YamlSourceReader): V = <<?[V](path) match {
    case None    ⇒ throwException(MissingPathValueError(path mkString "/"))
    case Some(v) ⇒ v
  }

  protected def <<?[V <: Any: ClassTag](path: YamlPath)(implicit source: YamlSourceReader): Option[V] = source.find[V](path)

  protected def <<-(implicit source: YamlSourceReader): YamlSourceReader = {
    val pull = source.pull()
    pull.foreach {
      case (key, _) ⇒ >>(key, None)
    }
    YamlSourceReader(pull)
  }

  protected def >>(path: YamlPath, value: Any)(implicit source: YamlSourceReader): Option[Any] = source.set(path, Option(value))

  protected def >>(path: YamlPath, value: Option[Any])(implicit source: YamlSourceReader): Option[Any] = source.set(path, value)

  protected def first[V <: Any: ClassTag](paths: List[String])(implicit source: YamlSourceReader): Option[V] = first[V](paths.map(string2Path): _*)

  protected def first[V <: Any: ClassTag](paths: YamlPath*)(implicit source: YamlSourceReader): Option[V] = paths.flatMap(<<?[V](_)).headOption

  protected def name(implicit source: YamlSourceReader): String = <<![String]("name")

  protected def reference(implicit source: YamlSourceReader): String = <<?[String]("reference").getOrElse(<<![String]("ref"))

  protected def hasReference(implicit source: YamlSourceReader): Option[String] = <<?[String]("reference").orElse(<<?[String]("ref"))

  protected def isReference(implicit source: YamlSourceReader): Boolean = hasReference.nonEmpty

  protected def expandToList(path: YamlPath)(implicit source: YamlSourceReader) = {
    <<?[Any](path) match {
      case None                 ⇒
      case Some(value: List[_]) ⇒
      case Some(value)          ⇒ >>(path, List(value))
    }
  }
}

trait ReferenceYamlReader[T] extends YamlReader[T] {

  def readReference: PartialFunction[Any, T]

  def readReferenceFromSource(any: Any): T = load(new StringReader(any.toString), readReference)
}

trait WeakReferenceYamlReader[T] extends YamlReader[T] {

  import YamlSourceReader._

  def readReferenceOrAnonymous(any: Any): T = any match {
    case string: String         ⇒ createReference(YamlSourceReader("reference" -> string))
    case yaml: YamlSourceReader ⇒ read(validateEitherReferenceOrAnonymous(yaml))
  }

  def readOptionalReferenceOrAnonymous(path: YamlPath)(implicit source: YamlSourceReader): Option[T] = <<?[Any](path).flatMap {
    reference ⇒ Some(readReferenceOrAnonymous(reference))
  }

  protected def validateEitherReferenceOrAnonymous(implicit source: YamlSourceReader): YamlSourceReader = {
    if (!isAnonymous && !isReference)
      throwException(EitherReferenceOrAnonymous(asReferenceOf, name))
    source
  }

  protected override def name(implicit source: YamlSourceReader): String = <<?[String]("name") match {
    case None        ⇒ ""
    case Some(value) ⇒ value
  }

  override protected def parse(implicit source: YamlSourceReader): T = if (isReference) createReference else createDefault

  protected def isAnonymous(implicit source: YamlSourceReader): Boolean = <<?[String]("name").isEmpty

  protected def `type`(implicit source: YamlSourceReader): String = <<![String]("type")

  protected def parameters(implicit source: YamlSourceReader): Map[String, Any] = source.flatten(_ != "type")

  protected def createReference(implicit source: YamlSourceReader): T

  protected def createDefault(implicit source: YamlSourceReader): T

  protected def asReferenceOf: String = getClass.getSimpleName.substring(0, getClass.getSimpleName.indexOf("Reader")).toLowerCase
}

trait TraitReader extends TraitResolver {
  this: YamlReader[_] ⇒

  def parseTraits[A <: Trait](source: Option[YamlSourceReader], mapper: (String, Option[String], Option[String]) ⇒ A, alias: Boolean): List[A] = {
    source match {
      case None ⇒ List[A]()
      case Some(yamlSourceReader: YamlSourceReader) ⇒ yamlSourceReader.pull().map {
        case (name, value) ⇒
          if (value.isInstanceOf[Map[_, _]] || value.isInstanceOf[List[_]])
            throwException(MalformedTraitError(name))

          val nameAlias = resolveNameAlias(name)
          mapper(nameAlias._1, if (alias) nameAlias._2 else None, if (value == null) None else Some(value.toString))
      } toList
    }
  }

  def ports(name: String = "ports", addGroup: Boolean = false)(implicit source: YamlSourceReader): List[Port] = {
    parseTraits(<<?[YamlSourceReader](name), { (name: String, alias: Option[String], value: Option[String]) ⇒
      val reference = if (addGroup) {
        NoGroupReference.referenceFor(name) match {
          case Some(ref) ⇒ ref.asTraitReference(TraitReference.Ports)
          case None      ⇒ name
        }
      } else name
      Port(reference, alias, value)
    }, false)
  }

  def environmentVariables(names: List[String] = List("environment_variables", "env"), alias: Boolean = true, addGroup: Boolean = false)(implicit source: YamlSourceReader): List[EnvironmentVariable] = {
    parseTraits(first[YamlSourceReader](names), { (name: String, alias: Option[String], value: Option[String]) ⇒
      val reference = if (addGroup) {
        NoGroupReference.referenceFor(name) match {
          case Some(ref) ⇒ ref.asTraitReference(TraitReference.EnvironmentVariables)
          case None      ⇒ name
        }
      } else name
      EnvironmentVariable(reference, alias, value)
    }, alias)
  }

  def constants(name: String = "constants", addGroup: Boolean = false)(implicit source: YamlSourceReader): List[Constant] = {
    parseTraits(<<?[YamlSourceReader](name), { (name: String, alias: Option[String], value: Option[String]) ⇒
      val reference = if (addGroup) {
        NoGroupReference.referenceFor(name) match {
          case Some(ref) ⇒ ref.asTraitReference(TraitReference.EnvironmentVariables)
          case None      ⇒ name
        }
      } else name
      Constant(reference, alias, value)
    }, false)
  }

  def hosts(name: String = "hosts")(implicit source: YamlSourceReader): List[Host] = {
    parseTraits(<<?[YamlSourceReader](name), { (name: String, alias: Option[String], value: Option[String]) ⇒
      Host(TraitReference(name, TraitReference.Hosts, Host.host).reference, value)
    }, false)
  }
}

trait DialectReader {
  this: YamlReader[_] ⇒

  def dialects(implicit source: YamlSourceReader): Map[Dialect.Value, Any] = {
    <<?[Any]("dialects") match {
      case Some(ds: YamlSourceReader) ⇒ dialectValues(ds)
      case _                          ⇒ Map()
    }
  }

  def dialectValues(implicit source: YamlSourceReader): Map[Dialect.Value, Any] = {
    Dialect.values.toList.flatMap { dialect ⇒
      <<?[Any](dialect.toString.toLowerCase) match {
        case None                      ⇒ Nil
        case Some(d: YamlSourceReader) ⇒ (dialect -> d.flatten()) :: Nil
        case Some(d)                   ⇒ (dialect -> Map()) :: Nil
      }
    } toMap
  }
}

