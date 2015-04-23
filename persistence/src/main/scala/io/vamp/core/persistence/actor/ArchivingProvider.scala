package io.vamp.core.persistence.actor

import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import io.vamp.common.akka.ExecutionContextProvider
import io.vamp.common.notification.NotificationProvider
import io.vamp.common.pulse.PulseClientProvider
import io.vamp.core.model.artifact._
import io.vamp.core.persistence.notification.ArtifactArchivingError
import io.vamp.pulse.api.Event

trait ArchivingProvider extends PulseClientProvider {
  this: NotificationProvider with ExecutionContextProvider =>

  implicit val timeout: Timeout

  val url = ConfigFactory.load().getString("vamp.core.persistence.pulse.url")

  def archiveCreate(artifact: Artifact, source: Option[String]): Artifact =
    if (source.isDefined) archive(artifact, source, s"archiving:create") else artifact

  def archiveUpdate(artifact: Artifact, source: Option[String]): Artifact =
    if (source.isDefined) archive(artifact, source, s"archiving:update") else artifact

  def archiveDelete(artifact: Artifact): Artifact = archive(artifact, None, s"archiving:delete")

  protected def archive(artifact: Artifact, source: Option[String], archiveTag: String) = {
    tagFor(artifact) match {
      case Some(artifactTag) => client.sendEvent(Event(artifactTag :: archiveTag :: Nil, source))
      case _ => exception(ArtifactArchivingError(artifact))
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
    case request => None
  }
}