package io.magnetic.vamp_core.model.reader

import java.io.{File, InputStream, Reader, StringReader}

import _root_.io.magnetic.vamp_common.notification.NotificationErrorException
import _root_.io.magnetic.vamp_core.model.notification._
import org.yaml.snakeyaml.Yaml

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Source
import scala.language.{implicitConversions, postfixOps}
import scala.reflect._

sealed trait YamlInput

case class StringInput(string: String) extends YamlInput

case class ReaderInput(reader: Reader) extends YamlInput

case class StreamInput(stream: InputStream) extends YamlInput

case class FileInput(file: File) extends YamlInput

object YamlInput {
  implicit def string2YamlInput(string: String): YamlInput = StringInput(string)

  implicit def reader2YamlInput(reader: Reader): YamlInput = ReaderInput(reader)

  implicit def stream2YamlInput(stream: InputStream): YamlInput = StreamInput(stream)

  implicit def file2YamlInput(file: File): YamlInput = FileInput(file)
}

trait YamlReader[T] extends ModelNotificationProvider {
  type YamlPath = List[String]
  type YamlList = List[YamlObject]
  type YamlObject = mutable.LinkedHashMap[String, Any]

  implicit def string2Path(path: String): YamlPath = path.split('/').toList

  def read(input: YamlInput): T = input match {
    case ReaderInput(reader) => read(reader)
    case StreamInput(stream) => read(Source.fromInputStream(stream).bufferedReader())
    case StringInput(string) => read(new StringReader(string), close = true)
    case FileInput(file) => read(Source.fromFile(file).bufferedReader(), close = true)
  }

  private def read(reader: Reader, close: Boolean = false): T = try {
    read(convert(new Yaml().load(reader)).asInstanceOf[YamlObject])
  } catch {
    case e: NotificationErrorException => throw e
    case e: Exception => error(YamlParsingError(e))
  }
  finally {
    if (close)
      reader.close()
  }

  private def convert(any: Any): Any = any match {
    case source: java.util.Map[_, _] =>
      val map = new YamlObject()
      source.entrySet().asScala.filter(entry => entry.getValue != null).foreach(entry => map += entry.getKey.toString -> convert(entry.getValue))
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
    case None => error(MissingPathValueError(path mkString "/"))
    case Some(v) => v
  }

  protected def <<?[V <: Any : ClassTag](path: YamlPath)(implicit source: YamlObject): Option[V] = path match {
    case last :: Nil => source.get(last) match {
      case None => None
      case Some(value: V) => Some(value.asInstanceOf[V])
      // if V == Double, conversion from Int to Double if Double is expected and Int provided.
      case Some(value: Int) if classTag[V].runtimeClass == classOf[Double] => Some(value.toDouble.asInstanceOf[V])
      // if V == String
      case Some(value) if classTag[V].runtimeClass == classOf[String] => Some(value.toString.asInstanceOf[V])
      // if V == Map
      case Some(value: collection.Map[_, _]) if classTag[V].runtimeClass == classOf[Map[_, _]] => Some(value.asInstanceOf[V])
      // if V == List
      case Some(value: List[_]) if classTag[V].runtimeClass == classOf[List[_]] => Some(value.asInstanceOf[V])
      case Some(failure) => error(UnexpectedTypeError(last, classTag[V].runtimeClass, failure.getClass))
    }

    case head :: tail => source.get(head).flatMap {
      case map: collection.Map[_, _] =>
        implicit val source = map.asInstanceOf[YamlObject]
        <<?[V](tail)
      case failure => error(UnexpectedInnerElementError(head, failure.getClass))
    }

    case Nil => None
  } // TODO: Use Vamp error handling approach.

  protected def put(path: YamlPath, value: Any)(implicit source: YamlObject): Option[Any] = >>(path, value)

  protected def >>(path: YamlPath, value: Any)(implicit source: YamlObject): Option[Any] = {

    def insert(key: String, path: List[String], value: Any) = {
      val next = new YamlObject()
      source.put(key, next)
      >>(path, value)(next)
    }

    path match {
      case Nil => None
      case last :: Nil => source.put(last, value)
      case head :: tail => source.get(head) match {
        case None => insert(head, tail, value)
        case Some(map: collection.Map[_, _]) => >>(tail, value)(map.asInstanceOf[YamlObject])
        case Some(_) => insert(head, tail, value)
      }
    }
  }

  protected def name(implicit source: YamlObject): String = <<![String]("name")

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
}

trait WeakReferenceYamlReader[T] extends ReferenceYamlReader[T] {

  override def readReference(any: Any): T = any match {
    case reference: String => createReference(new YamlObject() += ("name" -> reference))
    case map: collection.Map[_, _] => read(map.asInstanceOf[YamlObject])
  }

  def readOptionalReference(path: YamlPath)(implicit source: YamlObject): Option[T] = <<?[Any](path).flatMap {
    reference => Some(readReference(reference))
  }

  override protected def validate(implicit source: YamlObject): YamlObject = {
    if (!isAnonymous && source.size > 1)
      error(EitherReferenceOrAnonymous(asReferenceOf, reference))
    source
  }

  override protected def parse(implicit source: YamlObject): T = if (isAnonymous) createAnonymous else createReference

  protected def isAnonymous(implicit source: YamlObject): Boolean = <<?[String]("name").isEmpty

  protected def reference(implicit source: YamlObject): String = <<![String]("name")

  protected def `type`(implicit source: YamlObject): String = <<![String]("type")

  protected def parameters(implicit source: YamlObject): Map[String, Any] = source.filterKeys(_ != "type").toMap

  protected def createReference(implicit source: YamlObject): T

  protected def createAnonymous(implicit source: YamlObject): T

  protected def asReferenceOf: String = getClass.getSimpleName.substring(0, getClass.getSimpleName.indexOf("Reader")).toLowerCase
}

