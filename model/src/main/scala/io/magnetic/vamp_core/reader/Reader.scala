package io.magnetic.vamp_core.reader

import java.io.{File, InputStream, Reader, StringReader}

import scala.collection.JavaConverters._
import scala.io.Source
import scala.reflect._

sealed trait YamlInput

case class StringInput(string: String) extends YamlInput

case class ReaderInput(reader: Reader) extends YamlInput

case class StreamInput(stream: InputStream) extends YamlInput

case class FileInput(file: File) extends YamlInput

object YamlInput {
  implicit def string2StringInput(string: String): YamlInput = new StringInput(string)

  implicit def reader2StringInput(reader: Reader): YamlInput = new ReaderInput(reader)

  implicit def stream2StringInput(stream: InputStream): YamlInput = new StreamInput(stream)

  implicit def file2StringInput(file: File): YamlInput = new FileInput(file)
}

trait YamlReader[T] {

  def read(input: YamlInput): T = input match {
    case ReaderInput(reader) => read(reader)
    case StreamInput(stream) => read(Source.fromInputStream(stream).bufferedReader())
    case StringInput(string) => readAndClose(new StringReader(string))
    case FileInput(file) => readAndClose(Source.fromFile(file).bufferedReader())
  }

  protected def read(reader: Reader): T

  private def readAndClose(reader: Reader): T = try {
    read(reader)
  } finally {
    reader.close()
  }

  protected def get[V <: Any : ClassTag](path: List[String])(implicit input: Map[Any, Any]): Option[V] = {
    // TODO: Use Vamp error handling approach.
    path match {
      case last :: Nil => input.get(last) match {
        case None => None
        case Some(value: V) => Some(value.asInstanceOf[V])
        // if V == String
        case Some(value) if classTag[V].runtimeClass == classOf[String] && value != null => Some(value.toString.asInstanceOf[V])
        // if V == Map
        case Some(value)
          if classTag[V].runtimeClass == classOf[Map[_, _]] && value == null => None
        case Some(value)
          if classTag[V].runtimeClass == classOf[Map[_, _]] && classOf[java.util.Map[_, _]].isAssignableFrom(value.getClass) => Some(asMap[Any, Any](value).asInstanceOf[V])
        // if V == List
        case Some(value)
          if classTag[V].runtimeClass == classOf[List[_]] && value == null => None
        case Some(value)
          if classTag[V].runtimeClass == classOf[List[_]] && classOf[java.util.List[_]].isAssignableFrom(value.getClass) => Some(asList[Any](value).asInstanceOf[V])
        case Some(failure) => throw new IllegalArgumentException(s"Can't match type of $last, expected ${classTag[V].runtimeClass} not ${failure.getClass}")
      }

      case head :: tail => input.get(head).flatMap {
        case map: java.util.Map[_, _] =>
          implicit val input = asMap[Any, Any](map)
          get[V](tail)
        case failure => throw new IllegalArgumentException(s"Can't find a map entry for $head, found ${failure.getClass}")
      }

      case Nil => None
    }
  }

  protected def get[V <: Any : ClassTag](path: String)(implicit input: Map[Any, Any]): Option[V] = get(path.split('/').toList)

  protected def getOrError[V <: Any : ClassTag](path: String)(implicit input: Map[Any, Any]): V = get[V](path) match {
    case None => throw new IllegalArgumentException(s"Can't find entry for $path")
    case Some(v) => v
  } // TODO: Use Vamp error handling approach.

  protected def asList[A](any: Any): List[A] = any.asInstanceOf[java.util.List[_]].asScala.toList.asInstanceOf[List[A]]

  protected def asMap[A, B](any: Any): Map[A, B] = any.asInstanceOf[java.util.Map[_, _]].asScala.toMap.asInstanceOf[Map[A, B]]
}

