package io.vamp.core.model.reader

import java.io.{File, InputStream, Reader, StringReader}

import _root_.io.vamp.common.notification.NotificationErrorException
import _root_.io.vamp.core.model.artifact._
import _root_.io.vamp.core.model.notification._
import _root_.io.vamp.core.model.resolver.TraitResolver
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import org.yaml.snakeyaml.error.YAMLException

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Source
import scala.language.{implicitConversions, postfixOps}
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
  type YamlPath = List[String]
  type YamlList = List[YamlObject]
  type YamlObject = mutable.LinkedHashMap[String, Any]

  implicit def string2Path(path: String): YamlPath = path.split('/').toList

  def read(input: YamlSource): T = input match {
    case ReaderSource(reader) => read(reader)
    case StreamSource(stream) => read(Source.fromInputStream(stream).bufferedReader())
    case StringSource(string) => read(new StringReader(string), close = true)
    case FileSource(file) => read(Source.fromFile(file).bufferedReader(), close = true)
  }

  private def read(reader: Reader, close: Boolean = false): T = load(reader, close) match {
    case source: collection.Map[_, _] => read(source.asInstanceOf[YamlObject])
    case source => throwException(UnexpectedTypeError("/", classOf[YamlObject], if (source != null) source.getClass else classOf[Object]))
  }

  protected def load(reader: Reader, close: Boolean = false): Any = try {
    convert(yaml.load(reader))
  } catch {
    case e: NotificationErrorException => throw e
    case e: YAMLException => throwException(YamlParsingError(e.getMessage.replaceAll("java object", "resource"), e))
  }
  finally {
    if (close)
      reader.close()
  }

  protected def yaml = {
    new Yaml(new Constructor() {
      override def getClassForName(name: String): Class[_] = throw new YAMLException("Not supported.")
    })
  }

  private def convert(any: Any): Any = any match {
    case source: java.util.Map[_, _] =>
      val map = new YamlObject()
      source.entrySet().asScala.foreach(entry => map += entry.getKey.toString -> convert(entry.getValue))
      map
    case source: java.util.List[_] => source.asScala.map(convert).toList
    case source => source
  }

  def read(implicit source: YamlObject): T = validate(parse(validate(expand(source))))

  protected def expand(implicit source: YamlObject): YamlObject = source

  protected def validate(implicit source: YamlObject): YamlObject = source

  protected def parse(implicit source: YamlObject): T

  protected def validate(any: T): T = any

  protected def get[V <: Any : ClassTag](path: YamlPath)(implicit source: YamlObject): Option[V] = <<?[V](path)

  protected def getOrError[V <: Any : ClassTag](path: YamlPath)(implicit source: YamlObject): V = <<![V](path)

  protected def <<![V <: Any : ClassTag](path: YamlPath)(implicit source: YamlObject): V = <<?[V](path) match {
    case None => throwException(MissingPathValueError(path mkString "/"))
    case Some(v) => v
  }

  protected def <<?[V <: Any : ClassTag](path: YamlPath)(implicit source: YamlObject): Option[V] = path match {
    case last :: Nil => source.get(last) match {
      case None => None
      case Some(null) => None
      case Some(value: V) => Some(value.asInstanceOf[V])
      // if V == Double, conversion from Int to Double if Double is expected and Int provided.
      case Some(value: Int) if classTag[V].runtimeClass == classOf[Double] => Some(value.toDouble.asInstanceOf[V])
      // if V == String
      case Some(value) if classTag[V].runtimeClass == classOf[String] => Some(value.toString.asInstanceOf[V])
      // if V == Map
      case Some(value: collection.Map[_, _]) if classTag[V].runtimeClass == classOf[Map[_, _]] => Some(value.asInstanceOf[V])
      // if V == List
      case Some(value: List[_]) if classTag[V].runtimeClass == classOf[List[_]] => Some(value.asInstanceOf[V])
      case Some(failure) => throwException(UnexpectedTypeError(last, classTag[V].runtimeClass, failure.getClass))
    }

    case head :: tail => source.get(head).flatMap {
      case map: collection.Map[_, _] =>
        implicit val source = map.asInstanceOf[YamlObject]
        <<?[V](tail)
      case failure => throwException(UnexpectedInnerElementError(head, failure.getClass))
    }

    case Nil => None
  }

  protected def remove(path: YamlPath)(implicit source: YamlObject): Option[Any] = >>(path)

  protected def put(path: YamlPath, value: Any)(implicit source: YamlObject): Option[Any] = >>(path, value)

  protected def >>(path: YamlPath)(implicit source: YamlObject): Option[Any] = >>(path, None)

  protected def >>(path: YamlPath, value: Any)(implicit source: YamlObject): Option[Any] = >>(path, Some(value))

  protected def >>(path: YamlPath, value: Option[Any])(implicit source: YamlObject): Option[Any] = {

    def insert(key: String, path: List[String], value: Option[Any]) = {
      val next = new YamlObject()
      source.put(key, next)
      >>(path, value)(next)
    }

    path match {
      case Nil => None
      case last :: Nil => value match {
        case None => source.remove(last)
        case Some(v) => source.put(last, v)
      }
      case head :: tail => source.get(head) match {
        case None => insert(head, tail, value)
        case Some(map: collection.Map[_, _]) => >>(tail, value)(map.asInstanceOf[YamlObject])
        case Some(_) => insert(head, tail, value)
      }
    }
  }

  protected def first[V <: Any : ClassTag](paths: List[String])(implicit source: YamlObject): Option[V] = first[V](paths.map(string2Path): _*)

  protected def first[V <: Any : ClassTag](paths: YamlPath*)(implicit source: YamlObject): Option[V] = paths.flatMap(<<?[V](_)).headOption

  protected def name(implicit source: YamlObject): String = <<![String]("name")

  protected def reference(implicit source: YamlObject): String = <<?[String]("reference").getOrElse(<<![String]("ref"))

  protected def hasReference(implicit source: YamlObject): Option[String] = <<?[String]("reference").orElse(<<?[String]("ref"))

  protected def isReference(implicit source: YamlObject): Boolean = hasReference.nonEmpty

  protected def expandToList(path: YamlPath)(implicit source: YamlObject) = {
    <<?[Any](path) match {
      case None =>
      case Some(value: List[_]) =>
      case Some(value) => >>(path, List(value))
    }
  }
}

trait ReferenceYamlReader[T] extends YamlReader[T] {
  def readReference(any: Any): T

  def readReferenceFromSource(any: Any): T = readReference(load(new StringReader(any.toString), close = true))
}

trait WeakReferenceYamlReader[T] extends YamlReader[T] {

  def readReferenceOrAnonymous(any: Any): T = any match {
    case string: String => createReference(new YamlObject() += ("reference" -> string))
    case map: collection.Map[_, _] => read(validateEitherReferenceOrAnonymous(map.asInstanceOf[YamlObject]))
  }

  def readOptionalReferenceOrAnonymous(path: YamlPath)(implicit source: YamlObject): Option[T] = <<?[Any](path).flatMap {
    reference => Some(readReferenceOrAnonymous(reference))
  }

  protected def validateEitherReferenceOrAnonymous(implicit source: YamlObject): YamlObject = {
    if (!isAnonymous && !isReference)
      throwException(EitherReferenceOrAnonymous(asReferenceOf, name))
    source
  }

  protected override def name(implicit source: YamlObject): String = <<?[String]("name") match {
    case None => ""
    case Some(value) => value
  }

  override protected def parse(implicit source: YamlObject): T = if (isReference) createReference else createDefault

  protected def isAnonymous(implicit source: YamlObject): Boolean = <<?[String]("name").isEmpty

  protected def `type`(implicit source: YamlObject): String = <<![String]("type")

  protected def parameters(implicit source: YamlObject): Map[String, Any] = source.filterKeys(_ != "type").toMap

  protected def createReference(implicit source: YamlObject): T

  protected def createDefault(implicit source: YamlObject): T

  protected def asReferenceOf: String = getClass.getSimpleName.substring(0, getClass.getSimpleName.indexOf("Reader")).toLowerCase
}

trait TraitReader extends TraitResolver {
  this: YamlReader[_] =>

  def parseTraits[A <: Trait](source: Option[YamlObject], mapper: (String, Option[String], Option[String]) => A, alias: Boolean): List[A] = {
    source match {
      case None => List[A]()
      case Some(map: YamlObject) => map.map {
        case (name, value: AnyRef) if value.isInstanceOf[collection.Map[_, _]] || value.isInstanceOf[List[_]] => throwException(MalformedTraitError(name))
        case (name, value) =>
          val nameAlias = resolveNameAlias(name)
          mapper(nameAlias._1, if (alias) nameAlias._2 else None, if (value == null) None else Some(value.toString))
      } toList
    }
  }

  def ports(name: String = "ports", addGroup: Boolean = false)(implicit source: YamlObject): List[Port] = {
    parseTraits(<<?[YamlObject](name), { (name: String, alias: Option[String], value: Option[String]) =>
      val reference = if (addGroup) {
        NoGroupReference.referenceFor(name) match {
          case Some(ref) => ref.asTraitReference(TraitReference.Ports)
          case None => name
        }
      } else name
      Port(reference, alias, value)
    }, false)
  }

  def environmentVariables(names: List[String] = List("environment_variables", "env"), alias: Boolean = true, addGroup: Boolean = false)(implicit source: YamlObject): List[EnvironmentVariable] = {
    parseTraits(first[YamlObject](names), { (name: String, alias: Option[String], value: Option[String]) =>
      val reference = if (addGroup) {
        NoGroupReference.referenceFor(name) match {
          case Some(ref) => ref.asTraitReference(TraitReference.EnvironmentVariables)
          case None => name
        }
      } else name
      EnvironmentVariable(reference, alias, value)
    }, alias)
  }

  def constants(name: String = "constants", addGroup: Boolean = false)(implicit source: YamlObject): List[Constant] = {
    parseTraits(<<?[YamlObject](name), { (name: String, alias: Option[String], value: Option[String]) =>
      val reference = if (addGroup) {
        NoGroupReference.referenceFor(name) match {
          case Some(ref) => ref.asTraitReference(TraitReference.EnvironmentVariables)
          case None => name
        }
      } else name
      Constant(reference, alias, value)
    }, false)
  }

  def hosts(name: String = "hosts")(implicit source: YamlObject): List[Host] = {
    parseTraits(<<?[YamlObject](name), { (name: String, alias: Option[String], value: Option[String]) =>
      Host(TraitReference(name, TraitReference.Hosts, Host.host).reference, value)
    }, false)
  }
}

trait DialectReader {
  this: YamlReader[_] =>

  def dialects(implicit source: YamlObject): Map[Dialect.Value, Any] = {
    <<?[Any]("dialects") match {
      case Some(ds: collection.Map[_, _]) =>
        implicit val source = ds.asInstanceOf[YamlObject]
        dialectValues
      case _ => Map()
    }
  }

  def dialectValues(implicit source: YamlObject): Map[Dialect.Value, Any] = {
    Dialect.values.toList.flatMap(dialect => <<?[Any](dialect.toString.toLowerCase) match {
      case None => if (source.contains(dialect.toString.toLowerCase)) (dialect -> new YamlObject) :: Nil else Nil
      case Some(d: collection.Map[_, _]) => (dialect -> d.asInstanceOf[YamlObject]) :: Nil
      case Some(d) => (dialect -> new YamlObject) :: Nil
    }).toMap
  }
}

