package io.vamp.core.operation.workflow

import akka.actor.ActorRefFactory
import io.vamp.common.akka.{ActorSupport, ExecutionContextProvider}
import io.vamp.core.model.serialization.CoreSerializationFormat
import io.vamp.core.model.workflow.ScheduledWorkflow
import io.vamp.core.operation.notification.OperationNotificationProvider
import io.vamp.core.persistence.{PaginationSupport, PersistenceActor}
import org.json4s.Formats

import scala.concurrent.ExecutionContext

abstract class ApiContext(implicit scheduledWorkflow: ScheduledWorkflow, ec: ExecutionContext, arf: ActorRefFactory)
  extends ScriptingContext with PaginationSupport with ActorSupport with ExecutionContextProvider with OperationNotificationProvider {

  implicit def actorRefFactory = arf

  implicit def executionContext = ec

  implicit lazy val timeout = PersistenceActor.timeout

  implicit val formats: Formats = CoreSerializationFormat.default

  protected def nameOf(source: Any): String = source match {
    case map: java.util.Map[_, _] => map.asInstanceOf[java.util.Map[String, Any]].getOrDefault("name", "").toString
    case _ => ""
  }
}
