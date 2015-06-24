package io.vamp.core.operation.workflow

import akka.actor.ActorRefFactory
import io.vamp.common.akka.{ActorSupport, ExecutionContextProvider, FutureSupport}
import io.vamp.core.model.artifact.Artifact
import io.vamp.core.model.serialization.CoreSerializationFormat
import io.vamp.core.model.workflow.ScheduledWorkflow
import io.vamp.core.operation.controller.ArtifactApiController
import io.vamp.core.operation.notification.{OperationNotificationProvider, UnexpectedArtifact}
import io.vamp.core.persistence.{ArtifactResponseEnvelope, PersistenceActor}
import org.json4s.Formats

import scala.concurrent.ExecutionContext

class ArtifactApiContext(group: String)(implicit scheduledWorkflow: ScheduledWorkflow, ec: ExecutionContext, arf: ActorRefFactory)
  extends ScriptingContext with ArtifactApiController with ActorSupport with FutureSupport with ExecutionContextProvider with OperationNotificationProvider {

  implicit def actorRefFactory = arf

  implicit def executionContext = ec

  implicit lazy val timeout = PersistenceActor.timeout

  implicit val formats: Formats = CoreSerializationFormat.default

  def all() = serialize {
    val perPage = ArtifactResponseEnvelope.maxPerPage

    def allByPage(page: Int): (Long, List[Artifact]) = {
      offload(allArtifacts(group, page, perPage)) match {
        case ArtifactResponseEnvelope(list, count, _, _) => count -> list
      }
    }

    val (total, artifacts) = allByPage(1)

    if (total > artifacts.size)
      (2 until (total / perPage + (if (total % perPage == 0) 0 else 1)).toInt).foldRight(artifacts)((i, list) => list ++ allByPage(i)._2)
    else
      artifacts
  }

  def get(name: String) = serialize(offload(readArtifact(group, name)))

  def create(source: Any) = serialize(offload(createArtifact(group, load(source), validateOnly = false)))

  def update(source: Any) = serialize(offload(updateArtifact(group, nameOf(source), load(source), validateOnly = false)))

  def delete(source: Any) = serialize(offload(deleteArtifact(group, nameOf(source), load(source), validateOnly = false)))

  private def nameOf(source: Any): String = source match {
    case map: java.util.Map[_, _] => map.asInstanceOf[java.util.Map[String, Any]].getOrDefault("name", "").toString
    case _ => error(UnexpectedArtifact(group))
  }
}
