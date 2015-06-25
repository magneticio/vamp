package io.vamp.core.persistence

import akka.actor.Props
import akka.pattern.ask
import io.vamp.common.akka._
import io.vamp.core.model.artifact._
import io.vamp.core.model.workflow.{ScheduledWorkflow, Workflow}
import io.vamp.core.persistence.PersistenceActor._
import io.vamp.core.persistence.notification.ArtifactArchivingError
import io.vamp.core.pulse_driver.PulseDriverActor
import io.vamp.core.pulse_driver.model.Event

import scala.language.postfixOps

object ArchivePersistenceActor extends ActorDescription {
  def props(args: Any*): Props = Props(classOf[ArchivePersistenceActor], args: _*)
}

case class ArchivePersistenceInfo(`type`: String, artifacts: Map[String, Map[String, Any]])

class ArchivePersistenceActor(target: ActorDescription) extends DecoratorPersistenceActor(target) {

  override protected def infoMap() = Map("archive" -> true)

  override protected def create(artifact: Artifact, source: Option[String], ignoreIfExists: Boolean) =
    archiveCreate(offload(actorFor(target) ? Create(artifact, source, ignoreIfExists)).asInstanceOf[Artifact], source)

  override protected def update(artifact: Artifact, source: Option[String], create: Boolean) = {
    val a = offload(actorFor(target) ? Update(artifact, source, create)).asInstanceOf[Artifact]
    if (create) archiveCreate(a, source) else archiveUpdate(a, source)
  }

  override protected def delete(name: String, `type`: Class[_ <: Artifact]) =
    archiveDelete(offload(actorFor(target) ? Delete(name, `type`)).asInstanceOf[Artifact])

  private def archiveCreate(artifact: Artifact, source: Option[String]): Artifact =
    if (source.isDefined) archive(artifact, source, s"archiving:create") else artifact

  private def archiveUpdate(artifact: Artifact, source: Option[String]): Artifact =
    if (source.isDefined) archive(artifact, source, s"archiving:update") else artifact

  private def archiveDelete(artifact: Artifact): Artifact = archive(artifact, None, s"archiving:delete")

  private def archive(artifact: Artifact, source: Option[String], archiveTag: String) = {
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

