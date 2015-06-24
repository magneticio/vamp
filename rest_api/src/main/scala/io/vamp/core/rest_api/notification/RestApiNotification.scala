package io.vamp.core.rest_api.notification

import io.vamp.common.notification.Notification

case class UnexpectedEndOfRequest() extends Notification

case class UnsupportedRoutingWeightChangeError(weight: Int) extends Notification
