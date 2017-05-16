package io.vamp.persistence

import akka.pattern.ask
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

  def archiveDelete(name: String, `type`: Class[_ <: Artifact]): Unit = archive(name, `type`, None, archiveDeleteTag)

  private def archive(name: String, `type`: Class[_ <: Artifact], source: Option[String], archiveTag: String): Unit = {
    tagFor(name, `type`) match {
      case Some(artifactTag) ⇒
        val event = Event(Set(artifactTag, archiveTag), source, `type` = eventType)
        log.debug(s"Archive event with tags: ${event.tags}")
        IoC.actorFor[PulseActor] ? PulseActor.Publish(event)
      case _ ⇒
    }
  }

  private def tagFor(name: String, `type`: Class[_ <: Artifact]): Option[String] = tagFor(`type`) map { tag ⇒ s"$tag:$name" }
}

trait PersistenceTag {
  protected def tagFor(`type`: Class[_]): Option[String] = `type` match {
    case t if classOf[Deployment].isAssignableFrom(t) ⇒ Option("deployments")
    case t if classOf[DeploymentServiceScale].isAssignableFrom(t) ⇒ Option("deployment-service-scales")
    case t if classOf[Gateway].isAssignableFrom(t) ⇒ Option("gateways")
    case t if classOf[Breed].isAssignableFrom(t) ⇒ Option("breeds")
    case t if classOf[Blueprint].isAssignableFrom(t) ⇒ Option("blueprints")
    case t if classOf[Sla].isAssignableFrom(t) ⇒ Option("slas")
    case t if classOf[Scale].isAssignableFrom(t) ⇒ Option("scales")
    case t if classOf[Escalation].isAssignableFrom(t) ⇒ Option("escalations")
    case t if classOf[Route].isAssignableFrom(t) ⇒ Option("routes")
    case t if classOf[Condition].isAssignableFrom(t) ⇒ Option("conditions")
    case t if classOf[Workflow].isAssignableFrom(t) ⇒ Option("workflows")
    case t if classOf[WorkflowStatus].isAssignableFrom(t) ⇒ Option("workflow-statuses")
    case t if classOf[Template].isAssignableFrom(t) ⇒ Option("templates")
    case _ ⇒ None
  }
}
