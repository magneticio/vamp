package io.vamp.core.model.notification

import io.vamp.common.notification.{Notification, PulseEvent}
import io.vamp.core.model.artifact.{Deployment, DeploymentCluster}

trait SlaEvent extends PulseEvent {
  def deployment: Deployment

  def cluster: DeploymentCluster

  def slaTags = s"deployment:${deployment.name}" :: s"cluster:${cluster.name}" :: Nil
}

case class Escalate(deployment: Deployment, cluster: DeploymentCluster) extends Notification with SlaEvent {
  override def tags = "escalate" :: slaTags
}

case class DeEscalate(deployment: Deployment, cluster: DeploymentCluster) extends Notification with SlaEvent {
  override def tags = "deescalate" :: slaTags
}
