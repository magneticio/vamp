package io.vamp.persistence

import akka.util.Timeout
import io.vamp.common.Artifact
import io.vamp.common.akka._
import io.vamp.model.artifact._
import io.vamp.model.event.Event
import io.vamp.pulse.PulseActor

object PersistenceArchive {

  val eventType = "archive"

  val archiveCreateTag = "archive:create"

  val archiveUpdateTag = "archive:update"

  val archiveDeleteTag = "archive:delete"
}

trait PersistenceArchive extends PersistenceTag {
  this: CommonActorLogging with CommonProvider ⇒

  import PersistenceArchive._

  implicit def timeout: Timeout

  def archiveCreate(artifact: Artifact, source: Option[String]): Artifact = {
    if (source.isDefined) archive(artifact.name, artifact.getClass, source, archiveCreateTag)
    artifact
  }

  def archiveUpdate(artifact: Artifact, source: Option[String]): Artifact = {
    if (source.isDefined) archive(artifact.name, artifact.getClass, source, archiveUpdateTag)
    artifact
  }

  def archiveUpdate(artifactTag: String, source: String): Unit = archive(artifactTag, Option(source), archiveUpdateTag)

  def archiveDelete(name: String, `type`: Class[_ <: Artifact]): Unit = archive(name, `type`, None, archiveDeleteTag)

  private def archive(name: String, `type`: Class[_ <: Artifact], source: Option[String], archiveTag: String): Unit = {
    tagFor(name, `type`) foreach { archive(_, source, archiveTag) }
  }

  private def archive(artifactTag: String, source: Option[String], archiveTag: String): Unit = {
    val event = Event(Event.expandTags(Set(artifactTag, archiveTag)), source, `type` = eventType)
    log.debug(s"Archive event with tags: ${event.tags}")
    IoC.actorFor[PulseActor] ! PulseActor.Publish(event)
  }

  private def tagFor(name: String, `type`: Class[_ <: Artifact]): Option[String] = tagFor(`type`) map { tag ⇒ s"$tag:$name" }
}

trait PersistenceTag {
  protected def tagFor(`type`: Class[_]): Option[String] = `type` match {
    case t if classOf[Deployment].isAssignableFrom(t) ⇒ Option(Deployment.kind)
    case t if classOf[Gateway].isAssignableFrom(t) ⇒ Option(Gateway.kind)
    case t if classOf[Breed].isAssignableFrom(t) ⇒ Option(Breed.kind)
    case t if classOf[Blueprint].isAssignableFrom(t) ⇒ Option(Blueprint.kind)
    case t if classOf[Sla].isAssignableFrom(t) ⇒ Option(Sla.kind)
    case t if classOf[Scale].isAssignableFrom(t) ⇒ Option(Scale.kind)
    case t if classOf[Escalation].isAssignableFrom(t) ⇒ Option(Escalation.kind)
    case t if classOf[Route].isAssignableFrom(t) ⇒ Option(Route.kind)
    case t if classOf[Condition].isAssignableFrom(t) ⇒ Option(Condition.kind)
    case t if classOf[Workflow].isAssignableFrom(t) ⇒ Option(Workflow.kind)
    case t if classOf[Template].isAssignableFrom(t) ⇒ Option(Template.kind)
    case _ ⇒ None
  }
}
