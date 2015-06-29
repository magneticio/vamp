package io.vamp.core.operation.workflow

import akka.actor.ActorRefFactory
import io.vamp.common.akka.{ActorSupport, ExecutionContextProvider, FutureSupport}
import io.vamp.core.model.artifact.Artifact
import io.vamp.core.model.serialization.CoreSerializationFormat
import io.vamp.core.model.workflow.ScheduledWorkflow
import io.vamp.core.operation.notification.{InternalServerError, OperationNotificationProvider}
import io.vamp.core.persistence.{ArtifactResponseEnvelope, PersistenceActor}
import org.json4s.Formats

import scala.concurrent.{ExecutionContext, Future}

abstract class ApiContext(implicit scheduledWorkflow: ScheduledWorkflow, ec: ExecutionContext, arf: ActorRefFactory)
  extends ScriptingContext with ActorSupport with FutureSupport with ExecutionContextProvider with OperationNotificationProvider {

  implicit def actorRefFactory = arf

  implicit def executionContext = ec

  implicit lazy val timeout = PersistenceActor.timeout

  implicit val formats: Formats = CoreSerializationFormat.default

  protected def allPages(perPage: (Int, Int) => Future[Any]) = {
    val maxPerPage = ArtifactResponseEnvelope.maxPerPage

    def allByPage(page: Int): (Long, List[Artifact]) = {
      offload(perPage(page, maxPerPage)) match {
        case ArtifactResponseEnvelope(list, count, _, _) => count -> list
        case any => throwException(InternalServerError(any))
      }
    }

    val (total, artifacts) = allByPage(1)

    if (total > artifacts.size)
      (2 until (total / maxPerPage + (if (total % maxPerPage == 0) 0 else 1)).toInt).foldRight(artifacts)((i, list) => list ++ allByPage(i)._2)
    else
      artifacts
  }

  protected def nameOf(source: Any): String = source match {
    case map: java.util.Map[_, _] => map.asInstanceOf[java.util.Map[String, Any]].getOrDefault("name", "").toString
    case _ => ""
  }
}
