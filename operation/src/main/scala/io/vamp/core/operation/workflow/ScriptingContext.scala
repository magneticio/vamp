package io.vamp.core.operation.workflow

import com.typesafe.scalalogging.Logger
import io.vamp.core.model.workflow.ScheduledWorkflow
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext

abstract class ScriptingContext(scheduledWorkflow: ScheduledWorkflow)(implicit executionContext: ExecutionContext) {

  protected val logger = Logger(LoggerFactory.getLogger(scheduledWorkflow.name))

  protected def toJavaScript(any: Any): Any = any match {
    case list: List[_] => list.map(toJavaScript).asJava
    case map: Map[_, _] => map.map({
      case (key, value) => toJavaScript(key) -> toJavaScript(value)
    }).asJava
    case _ => any
  }

  protected def toScala(any: Any): Any = any match {
    case list: java.util.List[_] => list.asScala.map(toScala)
    case map: java.util.Map[_, _] => map.asScala.map({
      case (key, value) => toScala(key) -> toScala(value)
    }).asJava
    case _ => any
  }
}
