package io.vamp.model.notification

import java.time.OffsetDateTime

import io.vamp.common.notification.Notification
import io.vamp.model.artifact.{ Deployment, DeploymentCluster }

object SlaEvent {
  def slaTags(deployment: Deployment, cluster: DeploymentCluster) = ("sla" :: s"deployment:${deployment.name}" :: s"cluster:${cluster.name}" :: Nil).toSet
}

trait SlaEvent {
  def deployment: Deployment

  def cluster: DeploymentCluster

  def timestamp: OffsetDateTime

  def value: AnyRef = None

  def tags: Set[String] = Set()
}

object Escalate {
  def tags: Set[String] = Set("sla:escalate")
}

case class Escalate(deployment: Deployment, cluster: DeploymentCluster, timestamp: OffsetDateTime = OffsetDateTime.now()) extends Notification with SlaEvent {
  override def tags = Escalate.tags ++ SlaEvent.slaTags(deployment, cluster)
}

object DeEscalate {
  def tags: Set[String] = Set("sla:deescalate")
}

case class DeEscalate(deployment: Deployment, cluster: DeploymentCluster, timestamp: OffsetDateTime = OffsetDateTime.now()) extends Notification with SlaEvent {
  override def tags = DeEscalate.tags ++ SlaEvent.slaTags(deployment, cluster)
}
