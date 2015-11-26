package io.vamp.model.reader

import io.vamp.model.notification.{ ModelNotificationProvider, UnexpectedInnerElementError, UnexpectedTypeError }

import scala.collection.mutable
import scala.language.{ implicitConversions, postfixOps }
import scala.reflect._
import scala.util.{ Failure, Success, Try }

object YamlSourceReader {
  type YamlPath = List[String]
  type YamlList = List[YamlSourceReader]

  def apply(entries: (String, Any)*): YamlSourceReader = apply(Map(entries: _*))

  def apply(map: collection.Map[String, Any] = Map[String, Any]()): YamlSourceReader = new YamlSourceReader(map)
}

class YamlSourceReader(map: collection.Map[String, Any]) extends ModelNotificationProvider {

  import YamlSourceReader._

  private val source = {
    def transform(value: Any): Any = value match {
      case value: List[_]              ⇒ value.map(transform)
      case value: collection.Map[_, _] ⇒ YamlSourceReader(value.asInstanceOf[collection.Map[String, Any]])
      case any                         ⇒ any
    }

    new mutable.LinkedHashMap[String, Any]() ++= map.map { case (key, value) ⇒ key -> transform(value) }
  }

  private val _consumed = new mutable.LinkedHashMap[String, Any]()

  def find[V <: Any: ClassTag](path: YamlPath): Option[V] = find[V](this, path)

  def set(path: YamlPath, value: Option[Any]): Option[Any] = set(this, path, value)

  def push(yaml: YamlSourceReader) = yaml.source.foreach { case (key, value) ⇒ source.put(key, value) }

  def pull(accept: (String) ⇒ Boolean = (String) ⇒ true): Map[String, Any] = consume(source.filterKeys(accept).toMap)

  def flatten(accept: (String) ⇒ Boolean = (String) ⇒ true): Map[String, Any] = flatten(consume = true, accept)

  def size: Int = source.size

  def consumed: Map[String, _] = _consumed.map {
    case (key, value: YamlSourceReader) ⇒ key -> value.consumed
    case (key, value: List[_]) ⇒
      key -> value.map {
        case yaml: YamlSourceReader ⇒ yaml.consumed
        case any                    ⇒ any
      }
    case (key, value) ⇒ key -> value
  } toMap

  def notConsumed: Map[String, _] = {
//    val all = flatten(consume = false, (String) ⇒ true)
//
//    all.filterNot {
//      case (key, value) ⇒ _consumed.get(key).isDefined
//    }
    Map()
  }

  private def find[V <: Any: ClassTag](target: YamlSourceReader, path: YamlPath): Option[V] =
    path match {
      case last :: Nil ⇒ Try {
        target.source.get(last) match {
          case None ⇒ None
          case Some(null) ⇒ None
          case Some(value: V) ⇒ Some(value.asInstanceOf[V])
          // if V == Double, conversion from Int to Double if Double is expected and Int provided.
          case Some(value: Int) if classTag[V].runtimeClass == classOf[Double] ⇒ Some(value.toDouble.asInstanceOf[V])
          // if V == String
          case Some(value) if classTag[V].runtimeClass == classOf[String] ⇒ Some(value.toString.asInstanceOf[V])
          // if V == Map
          case Some(value: YamlSourceReader) if classTag[V].runtimeClass == classOf[Map[_, _]] ⇒ Some(value.pull().asInstanceOf[V])
          // if V == List
          case Some(value: List[_]) if classTag[V].runtimeClass == classOf[List[_]] ⇒ Some(value.asInstanceOf[V])
          case Some(failure) ⇒ throwException(UnexpectedTypeError(last, classTag[V].runtimeClass, failure.getClass))
        }
      } match {
        case Success(value) ⇒
          if (value.isDefined) _consumed.put(last, value.get)
          value
        case Failure(e) ⇒ throw e
      }

      case head :: tail ⇒ target.source.get(head).flatMap {
        case yaml: YamlSourceReader ⇒ find[V](yaml, tail)
        case failure                ⇒ throwException(UnexpectedInnerElementError(head, failure.getClass))
      }

      case Nil ⇒ None
    }

  private def set(target: YamlSourceReader, path: YamlPath, value: Option[Any]): Option[Any] = {

    def insert(key: String, path: List[String], value: Option[Any]) = {
      val next = YamlSourceReader()
      target.source.put(key, next)
      set(next, path, value)
    }

    path match {
      case Nil ⇒ None
      case last :: Nil ⇒
        value match {
          case None    ⇒ target.source.remove(last)
          case Some(v) ⇒ target.source.put(last, v)
        }
      case head :: tail ⇒
        target.source.get(head) match {
          case None                         ⇒ insert(head, tail, value)
          case Some(yaml: YamlSourceReader) ⇒ set(yaml, tail, value)
          case Some(_)                      ⇒ insert(head, tail, value)
        }
    }
  }

  private def flatten(consume: Boolean, accept: (String) ⇒ Boolean): Map[String, Any] = {
    val result = source.filterKeys(accept).map {
      case (key, value: YamlSourceReader) ⇒ key -> value.flatten()
      case (key, value: List[_]) ⇒
        key -> value.map {
          case yaml: YamlSourceReader ⇒ yaml.flatten()
          case any                    ⇒ any
        }
      case (key, value) ⇒ key -> value
    } toMap

    if (consume) this.consume(result) else result
  }

  private def consume(map: Map[String, Any]) = {
    map.map { case (key, value) ⇒ _consumed.put(key, value) }
    map
  }
}
