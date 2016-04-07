package io.vamp.model.notification

import io.vamp.common.notification.Notification
import io.vamp.model.artifact.DefaultScale

trait WorkflowNotification extends Notification

object UndefinedWorkflowTriggerError extends WorkflowNotification

object BothWorkflowAndScriptError extends WorkflowNotification

case class IllegalPeriod(period: String) extends Notification

case class NoWorkflowRunnable(name: String) extends Notification

case class InvalidWorkflowScale(scale: DefaultScale) extends Notification
