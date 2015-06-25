package io.vamp.core.persistence

import akka.actor.Props
import akka.pattern.ask
import io.vamp.common.akka.Bootstrap.{Shutdown, Start}
import io.vamp.common.akka._
import io.vamp.common.vitals.InfoRequest
import io.vamp.core.model.artifact._
import io.vamp.core.model.workflow.{ScheduledWorkflow, Workflow}
import io.vamp.core.persistence.PersistenceActor._
import io.vamp.core.persistence.notification.{ArtifactArchivingError, PersistenceNotificationProvider}
import io.vamp.core.pulse_driver.PulseDriverActor
import io.vamp.core.pulse_driver.model.Event

import scala.language.postfixOps

object ArchivePersistenceActor extends ActorDescription {
  def props(args: Any*): Props = Props(classOf[ArchivePersistenceActor], args: _*)
}

case class ArchivePersistenceInfo(`type`: String, artifacts: Map[String, Map[String, Any]])

class ArchivePersistenceActor(target: ActorDescription) extends PersistenceReplyActor with CommonSupportForActors with PersistenceNotificationProvider {

  protected def respond(request: Any): Any = request match {

    case Start => actorFor(target) ! Start

    case Shutdown => actorFor(target) ! Shutdown

    case InfoRequest => offload(actorFor(target) ? InfoRequest)

    case All(ofType) => offload(actorFor(target) ? All(ofType))

    case AllPaginated(ofType, page, perPage) => offload(actorFor(target) ? AllPaginated(ofType, page, perPage))

    case Create(artifact, source, ignoreIfExists) => offload(actorFor(target) ? Create(artifact, source, ignoreIfExists)) match {
      case a: Artifact => archiveCreate(a, source)
      case other => other
    }

    case Read(name, ofType) => offload(actorFor(target) ? Read(name, ofType))

    case ReadExpanded(name, ofType) => offload(actorFor(target) ? ReadExpanded(name, ofType))

    case Update(artifact, source, create) => offload(actorFor(target) ? Update(artifact, source, create)) match {
      case a: Artifact => if (create) archiveCreate(a, source) else archiveUpdate(a, source)
      case other => other
    }

    case Delete(name, ofType) => offload(actorFor(target) ? Delete(name, ofType)) match {
      case a: Artifact => archiveDelete(a)
      case other => other
    }

    case _ => error(errorRequest(request))
  }

  protected def archiveCreate(artifact: Artifact, source: Option[String]): Artifact =
    if (source.isDefined) archive(artifact, source, s"archiving:create") else artifact

  protected def archiveUpdate(artifact: Artifact, source: Option[String]): Artifact =
    if (source.isDefined) archive(artifact, source, s"archiving:update") else artifact

  protected def archiveDelete(artifact: Artifact): Artifact = archive(artifact, None, s"archiving:delete")

  protected def archive(artifact: Artifact, source: Option[String], archiveTag: String) = {
    tagFor(artifact) match {
      case Some(artifactTag) =>
        val event = Event(Set(artifactTag, archiveTag), source)
        log.debug(s"Archive event with tags: ${event.tags}")
        actorFor(PulseDriverActor) ! PulseDriverActor.Publish(event)
      case _ =>
        exception(ArtifactArchivingError(artifact))
    }
    artifact
  }

  protected def tagFor(artifact: Artifact): Option[String] = artifact.getClass match {
    case t if classOf[Deployment].isAssignableFrom(t) => Some(s"deployments:${artifact.name}")
    case t if classOf[Breed].isAssignableFrom(t) => Some(s"breeds:${artifact.name}")
    case t if classOf[Blueprint].isAssignableFrom(t) => Some(s"blueprints:${artifact.name}")
    case t if classOf[Sla].isAssignableFrom(t) => Some(s"slas:${artifact.name}")
    case t if classOf[Scale].isAssignableFrom(t) => Some(s"scales:${artifact.name}")
    case t if classOf[Escalation].isAssignableFrom(t) => Some(s"escalations:${artifact.name}")
    case t if classOf[Routing].isAssignableFrom(t) => Some(s"routings:${artifact.name}")
    case t if classOf[Filter].isAssignableFrom(t) => Some(s"filters:${artifact.name}")
    case t if classOf[Workflow].isAssignableFrom(t) => Some(s"workflows:${artifact.name}")
    case t if classOf[ScheduledWorkflow].isAssignableFrom(t) => Some(s"scheduled-workflows:${artifact.name}")
    case request => None
  }
}

