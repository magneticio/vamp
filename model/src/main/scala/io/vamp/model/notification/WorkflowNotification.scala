package io.vamp.model.notification

import io.vamp.common.notification.Notification

trait WorkflowNotification extends Notification

object UndefinedWorkflowScheduleError extends WorkflowNotification

case class IllegalWorkflowSchedulePeriod(period: String) extends Notification
