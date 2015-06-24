package io.vamp.core.operation.workflow

import com.typesafe.scalalogging.Logger
import io.vamp.core.model.workflow.ScheduledWorkflow
import org.json4s.native.Serialization._
import org.json4s.{DefaultFormats, Formats}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.language.implicitConversions
import scala.reflect.Manifest

abstract class ScriptingContext(implicit scheduledWorkflow: ScheduledWorkflow, executionContext: ExecutionContext) {

  protected val logger = Logger(LoggerFactory.getLogger(scheduledWorkflow.name))

  protected def serialize(magnet: SerializationMagnet)(implicit formats: Formats = DefaultFormats): Any = magnet.serialize

  protected def load(magnet: SerializationMagnet)(implicit formats: Formats = DefaultFormats): String = magnet.load
}

trait SerializationMagnet {
  def serialize: Any

  def load: String
}

object SerializationMagnet {
  implicit def apply(any: => Any)(implicit mf: Manifest[Any], formats: Formats = DefaultFormats) = new SerializationMagnet {

    def serialize = {
      val response = any match {
        case anyRef: AnyRef => read[Any](write(anyRef))(DefaultFormats, mf)
        case _ => any
      }
      toJavaScript(response)
    }

    def load = toScala(any) match {
      case anyRef: AnyRef => write(anyRef)(DefaultFormats)
      case _ => any.toString
    }

    protected def toJavaScript(any: Any): Any = any match {
      case list: List[_] => list.map(toJavaScript).asJava.toArray
      case map: Map[_, _] => map.map({
        case (key, value) => toJavaScript(key) -> toJavaScript(value)
      }).asJava
      case _ => any
    }

    protected def toScala(any: Any): Any = any match {
      case list: java.util.List[_] => list.asScala.map(toScala)
      case map: java.util.Map[_, _] => map.asScala.map({
        case (key, value) => toScala(key) -> toScala(value)
      })
      case _ => any
    }
  }
}
