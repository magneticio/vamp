package io.vamp.core.model.notification

import java.time.OffsetDateTime

import io.vamp.common.notification.{Notification, PulseEvent}
import io.vamp.core.model.artifact.{Deployment, DeploymentCluster}

object SlaEvent {
  def slaTags(deployment: Deployment, cluster: DeploymentCluster) = s"deployment:${deployment.name}" :: s"cluster:${cluster.name}" :: Nil
}

trait SlaEvent extends PulseEvent {
  def deployment: Deployment

  def cluster: DeploymentCluster

  def timestamp: OffsetDateTime

  override def value: AnyRef = getClass.getSimpleName.toLowerCase
}

object Escalate {
  def tags = "escalate" :: Nil
}

case class Escalate(deployment: Deployment, cluster: DeploymentCluster, timestamp: OffsetDateTime = OffsetDateTime.now()) extends Notification with SlaEvent {
  override def tags = Escalate.tags ++ SlaEvent.slaTags(deployment, cluster)
}

object DeEscalate {
  def tags = "deescalate" :: Nil
}

case class DeEscalate(deployment: Deployment, cluster: DeploymentCluster, timestamp: OffsetDateTime = OffsetDateTime.now()) extends Notification with SlaEvent {
  override def tags = DeEscalate.tags ++ SlaEvent.slaTags(deployment, cluster)
}
