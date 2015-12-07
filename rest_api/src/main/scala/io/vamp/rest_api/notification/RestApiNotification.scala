package io.vamp.rest_api.notification

import io.vamp.common.notification.Notification

case class UnexpectedEndOfRequest() extends Notification

case class UnsupportedRouteWeightChangeError(weight: Int) extends Notification
