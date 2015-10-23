package io.vamp.model.notification

import io.vamp.common.notification.Notification

object NoTagEventError extends Notification

case class EventTimestampError(timestamp: String) extends Notification

object EventQueryTimeError extends Notification

case class UnsupportedAggregatorError(aggregator: String) extends Notification

