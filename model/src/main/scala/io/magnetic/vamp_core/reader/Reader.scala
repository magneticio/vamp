package io.magnetic.vamp_core.reader

import java.io.{File, InputStream, Reader, StringReader}

import org.yaml.snakeyaml.Yaml

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.io.Source
import scala.reflect._

sealed trait YamlInput

case class StringInput(string: String) extends YamlInput

case class ReaderInput(reader: Reader) extends YamlInput

case class StreamInput(stream: InputStream) extends YamlInput

case class FileInput(file: File) extends YamlInput

object YamlInput {
  implicit def string2StringInput(string: String): YamlInput = StringInput(string)

  implicit def reader2StringInput(reader: Reader): YamlInput = ReaderInput(reader)

  implicit def stream2StringInput(stream: InputStream): YamlInput = StreamInput(stream)

  implicit def file2StringInput(file: File): YamlInput = FileInput(file)
}

trait YamlReader[T] {
  type Path = List[String]
  type YamlSource = mutable.LinkedHashMap[String, Any]

  implicit def string2Path(path: String): Path = path.split('/').toList
  
  def read(input: YamlInput): T = input match {
    case ReaderInput(reader) => read(reader)
    case StreamInput(stream) => read(Source.fromInputStream(stream).bufferedReader())
    case StringInput(string) => read(new StringReader(string), close = true)
    case FileInput(file) => read(Source.fromFile(file).bufferedReader(), close = true)
  }

  private def read(reader: Reader, close: Boolean = false): T = try {
    implicit val input = convert(new Yaml().load(reader)).asInstanceOf[YamlSource]
    expand
    parse
  } finally {
    if (close)
      reader.close()
  }

  private def convert(any: Any): Any = any match {
    case source: java.util.Map[_, _] =>
      val map = new YamlSource()
      source.entrySet().asScala.filter(entry => entry.getValue != null).foreach(entry => map += entry.getKey.toString -> convert(entry.getValue))
      map
    case source: java.util.List[_] => source.asScala.map(convert).toList
    case source => source
  }

  protected def expand(implicit input: YamlSource) = {}

  protected def parse(implicit input: YamlSource): T

  protected def get[V <: Any : ClassTag](path: Path)(implicit input: YamlSource): Option[V] = <<?[V](path)

  protected def getOrError[V <: Any : ClassTag](path: Path)(implicit input: YamlSource): V = <<![V](path)

  protected def <<![V <: Any : ClassTag](path: Path)(implicit input: YamlSource): V = <<?[V](path) match {
    case None => throw new IllegalArgumentException(s"Can't find entry for $path")
    case Some(v) => v
  } // TODO: Use Vamp error handling approach.

  protected def <<?[V <: Any : ClassTag](path: Path)(implicit input: YamlSource): Option[V] = path match {
    case last :: Nil => input.get(last) match {
      case None => None
      case Some(value: V) => Some(value.asInstanceOf[V])
      // if V == String
      case Some(value) if classTag[V].runtimeClass == classOf[String] => Some(value.toString.asInstanceOf[V])
      // if V == Map
      case Some(value: collection.Map[_, _]) if classTag[V].runtimeClass == classOf[Map[_, _]] => Some(value.asInstanceOf[V])
      // if V == List
      case Some(value: List[_]) if classTag[V].runtimeClass == classOf[List[_]] => Some(value.asInstanceOf[V])
      case Some(failure) => throw new IllegalArgumentException(s"Can't match type of $last, expected ${classTag[V].runtimeClass} not ${failure.getClass}")
    }

    case head :: tail => input.get(head).flatMap {
      case map: collection.Map[_, _] =>
        implicit val input = map.asInstanceOf[YamlSource]
        <<?[V](tail)
      case failure => throw new IllegalArgumentException(s"Can't find a map entry for $head, found ${failure.getClass}")
    }

    case Nil => None
  } // TODO: Use Vamp error handling approach.


  protected def put(path: Path, value: Any)(implicit input: YamlSource): Option[Any] = >>(path, value)

  protected def >>(path: Path, value: Any)(implicit input: YamlSource): Option[Any] = path match {
    case Nil => None
    case last :: Nil => input.put(last, value)
    case head :: tail => input.get(head).flatMap {
      case map: collection.Map[_, _] =>
        implicit val input = map.asInstanceOf[YamlSource]
        >>(tail, value)
      case failure => throw new IllegalArgumentException(s"Can't find a map entry for $head, found ${failure.getClass}")
    }
  } // TODO: Use Vamp error handling approach.
}

