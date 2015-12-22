package io.vamp.operation.workflow

import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import io.vamp.model.workflow.ScheduledWorkflow
import org.json4s.native.Serialization._
import org.json4s.{ DefaultFormats, Formats }
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.language.implicitConversions
import scala.reflect.Manifest
import scala.util.{ Failure, Success }

abstract class ScriptingContext(implicit scheduledWorkflow: ScheduledWorkflow, executionContext: ExecutionContext) {

  protected val logger = Logger(LoggerFactory.getLogger(scheduledWorkflow.name))

  protected def serialize(magnet: SerializationMagnet)(implicit timeout: Timeout, formats: Formats = DefaultFormats): Any = magnet.serialize

  protected def load(magnet: SerializationMagnet)(implicit formats: Formats = DefaultFormats): String = magnet.load

  protected def asScala(magnet: SerializationMagnet)(implicit formats: Formats = DefaultFormats): Any = magnet.asScala
}

trait SerializationMagnet {
  def serialize: Any

  def load: String

  def asScala: Any
}

object SerializationMagnet {
  implicit def apply(any: ⇒ Any)(implicit timeout: Timeout, mf: Manifest[Any], formats: Formats = DefaultFormats) = new SerializationMagnet {
    def serialize = SerializationMagnet.serialize(any)

    def load = SerializationMagnet.load(any)

    def asScala = SerializationMagnet.toScala(any)
  }

  protected def serialize(any: Any)(implicit timeout: Timeout, mf: Manifest[Any], formats: Formats = DefaultFormats): Any = toJavaScript(any match {
    case future: Future[_] ⇒ serialize(offload(future))
    case list: Seq[_]      ⇒ list.map(serialize)
    case anyRef: AnyRef ⇒
      val string = write(anyRef)
      if (string.startsWith("{")) read[Any](string)(DefaultFormats, mf) else anyRef
    case _ ⇒ any
  })

  protected def load(any: Any): String = toScala(any) match {
    case anyRef: AnyRef ⇒ write(anyRef)(DefaultFormats)
    case _              ⇒ any.toString
  }

  protected def toJavaScript(any: Any): Any = any match {
    case list: Seq[_] ⇒ list.map(toJavaScript).asJava.toArray
    case map: Map[_, _] ⇒ map.map({
      case (key, value) ⇒ toJavaScript(key) -> toJavaScript(value)
    }).asJava
    case _ ⇒ any
  }

  protected def toScala(any: Any): Any = any match {
    case l: java.util.Collection[_] ⇒ l.asScala.map(toScala)
    case m: java.util.Map[_, _] ⇒
      val map = m.asScala.map({
        case (key, value) ⇒ toScala(key) -> toScala(value)
      })
      if (map.keys.forall {
        case key: String ⇒ key.forall(Character.isDigit)
        case _           ⇒ false
      }) map.toSeq.sortBy(_._1.toString.toInt).map(_._2).toList
      else map

    case _ ⇒ any
  }

  @deprecated("Blocking", "0.7.10")
  private def offload(future: Future[Any])(implicit timeout: Timeout): Any = {
    Await.ready(future, timeout.duration)
    future.value.get match {
      case Success(result) ⇒ result
      case Failure(result) ⇒ result
    }
  }
}
