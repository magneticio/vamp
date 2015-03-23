package io.vamp.core.model.notification

import io.vamp.common.notification.Notification
import io.vamp.core.model.artifact.{Deployment, DeploymentCluster, Sla}

trait SlaNotificationEvent

case class Escalate(deployment: Deployment, cluster: DeploymentCluster, sla: Sla) extends Notification with SlaNotificationEvent

case class DeEscalate(deployment: Deployment, cluster: DeploymentCluster, sla: Sla) extends Notification with SlaNotificationEvent
