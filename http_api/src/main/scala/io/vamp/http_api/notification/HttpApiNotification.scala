package io.vamp.http_api.notification

import io.vamp.common.notification.{ ErrorNotification, Notification }

case class InternalServerError(reason: Any) extends Notification with ErrorNotification

case class BadRequestError(message: String) extends Notification
