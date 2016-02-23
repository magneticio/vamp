package io.vamp.persistence.db

import akka.actor.ActorLogging
import akka.pattern.ask
import akka.util.Timeout
import io.vamp.common.akka._
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._
import io.vamp.model.event.Event
import io.vamp.model.workflow.{ ScheduledWorkflow, Workflow }
import io.vamp.pulse.PulseActor

import scala.language.postfixOps

object PersistenceArchive {

  val archiveCreateTag = "archive:create"

  val archiveUpdateTag = "archive:update"

  val archiveDeleteTag = "archive:delete"

  def tagFor(`type`: Class[_]): Option[String] = `type` match {
    case t if classOf[Deployment].isAssignableFrom(t) ⇒ Option("deployments")
    case t if classOf[Gateway].isAssignableFrom(t) ⇒ Option("gateways")
    case t if classOf[Breed].isAssignableFrom(t) ⇒ Option("breeds")
    case t if classOf[Blueprint].isAssignableFrom(t) ⇒ Option("blueprints")
    case t if classOf[Sla].isAssignableFrom(t) ⇒ Option("slas")
    case t if classOf[Scale].isAssignableFrom(t) ⇒ Option("scales")
    case t if classOf[Escalation].isAssignableFrom(t) ⇒ Option("escalations")
    case t if classOf[Route].isAssignableFrom(t) ⇒ Option("routes")
    case t if classOf[Filter].isAssignableFrom(t) ⇒ Option("filters")
    case t if classOf[Workflow].isAssignableFrom(t) ⇒ Option("workflows")
    case t if classOf[ScheduledWorkflow].isAssignableFrom(t) ⇒ Option("scheduled-workflows")
    case request ⇒ None
  }
}

trait PersistenceArchive {
  this: ActorSystemProvider with ActorLogging with NotificationProvider ⇒

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
        val event = Event(Set(artifactTag, archiveTag), source)
        log.debug(s"Archive event with tags: ${event.tags}")
        IoC.actorFor[PulseActor] ? PulseActor.Publish(event)
      case _ ⇒
    }
  }

  private def tagFor(name: String, `type`: Class[_ <: Artifact]): Option[String] = PersistenceArchive.tagFor(`type`) map {
    case tag ⇒ s"$tag:$name"
  }
}
