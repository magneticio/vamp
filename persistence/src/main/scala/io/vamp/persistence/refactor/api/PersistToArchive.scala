package io.vamp.persistence.refactor.api

import akka.actor.ActorSystem
import io.vamp.common.{ Namespace, UnitPlaceholder }
import io.vamp.common.akka.IoC
import io.vamp.model.event.Event
import io.vamp.persistence.PersistenceArchive.{ archiveCreateTag, archiveDeleteTag, archiveUpdateTag, eventType }
import io.vamp.pulse.PulseActor
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.model.artifact._
import io.vamp.persistence.{ DeploymentServiceScale, WorkflowStatus }

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * Created by mihai on 1/16/18.
 */
trait PersistToArchive {

  implicit def timeout: Timeout = Timeout(5.seconds)

  def archiveCreate[T](name: String, artifact: Any, sourceAsString: String)(implicit ns: Namespace, as: ActorSystem): Future[UnitPlaceholder] =
    archive(name, artifact.getClass, Some(sourceAsString), archiveCreateTag)

  def archiveUpdate(name: String, artifact: Any, sourceAsString: String)(implicit ns: Namespace, as: ActorSystem): Future[UnitPlaceholder] =
    archive(name, artifact.getClass, Some(sourceAsString), archiveUpdateTag)

  def archiveDelete(name: String, artifact: Any)(implicit ns: Namespace, as: ActorSystem): Future[UnitPlaceholder] =
    archive(name, artifact.getClass, None, archiveDeleteTag)

  private def archive(name: String, `type`: Class[_], sourceAsString: Option[String], archiveTag: String)(implicit ns: Namespace, as: ActorSystem): Future[UnitPlaceholder] = {
    tagFor(name, `type`) match {
      case Some(artifactTag) ⇒
        val event = Event(Set(artifactTag, archiveTag), sourceAsString.map(_.filterNot(_ == '\"')), `type` = eventType)
        (IoC.actorFor[PulseActor] ? PulseActor.Publish(event)).map(_ ⇒ UnitPlaceholder)
      case _ ⇒ Future.successful(UnitPlaceholder)
    }
  }

  private def tagFor(name: String, `type`: Class[_]): Option[String] = tagFor(`type`) map { tag ⇒ s"$tag:$name" }

  private def tagFor(`type`: Class[_]): Option[String] = `type` match {
    case t if classOf[Deployment].isAssignableFrom(t) ⇒ Option(Deployment.kind)
    case t if classOf[DeploymentServiceScale].isAssignableFrom(t) ⇒ Option(DeploymentServiceScale.kind)
    case t if classOf[Gateway].isAssignableFrom(t) ⇒ Option(Gateway.kind)
    case t if classOf[Breed].isAssignableFrom(t) ⇒ Option(Breed.kind)
    case t if classOf[Blueprint].isAssignableFrom(t) ⇒ Option(Blueprint.kind)
    case t if classOf[Sla].isAssignableFrom(t) ⇒ Option(Sla.kind)
    case t if classOf[Scale].isAssignableFrom(t) ⇒ Option(Scale.kind)
    case t if classOf[Escalation].isAssignableFrom(t) ⇒ Option(Escalation.kind)
    case t if classOf[Route].isAssignableFrom(t) ⇒ Option(Route.kind)
    case t if classOf[Condition].isAssignableFrom(t) ⇒ Option(Condition.kind)
    case t if classOf[Workflow].isAssignableFrom(t) ⇒ Option(Workflow.kind)
    case t if classOf[WorkflowStatus].isAssignableFrom(t) ⇒ Option(WorkflowStatus.kind)
    case t if classOf[Template].isAssignableFrom(t) ⇒ Option(Template.kind)
    case _ ⇒ None
  }
}
