package io.vamp.core.persistence

import akka.actor.{Actor, ActorLogging}
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka._
import io.vamp.common.notification.NotificationProvider
import io.vamp.core.model.artifact._
import io.vamp.core.model.event.Event
import io.vamp.core.model.workflow.{ScheduledWorkflow, Workflow}
import io.vamp.core.persistence.notification.ArtifactArchivingError
import io.vamp.core.pulse.PulseActor

import scala.language.postfixOps

trait PersistenceArchiving {
  this: ActorSystemProvider with ActorLogging with NotificationProvider =>

  implicit def timeout: Timeout

  def archiveCreate(artifact: Artifact, source: Option[String]): Artifact =
    if (source.isDefined) archive(artifact, source, s"archiving:create") else artifact

  def archiveUpdate(artifact: Artifact, source: Option[String]): Artifact =
    if (source.isDefined) archive(artifact, source, s"archiving:update") else artifact

  def archiveDelete(artifact: Option[Artifact]): Option[Artifact] = artifact.flatMap(a => Some(archive(a, None, s"archiving:delete")))

  private def archive(artifact: Artifact, source: Option[String], archiveTag: String) = {
    tagFor(artifact) match {
      case Some(artifactTag) =>
        val event = Event(Set(artifactTag, archiveTag), source)
        log.debug(s"Archive event with tags: ${event.tags}")
        IoC.actorFor(PulseActor) ? PulseActor.Publish(event)
      case _ =>
        reportException(ArtifactArchivingError(artifact))
    }
    artifact
  }

  private def tagFor(artifact: Artifact): Option[String] = artifact.getClass match {
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

