package io.vamp.core.operation.notification

import io.vamp.core.model.artifact.{Deployment, Sla, DeploymentCluster}
import io.vamp.common.notification.Notification

case class Escalate(deployment: Deployment, cluster: DeploymentCluster, sla: Sla) extends Notification

case class DeEscalate(deployment: Deployment, cluster: DeploymentCluster, sla: Sla) extends Notification
