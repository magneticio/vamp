package io.magnetic.vamp_core.model.notification

import io.magnetic.vamp_common.notification.Notification
import io.magnetic.vamp_core.model.{Trait, Breed, EnvironmentVariable, Port}

case class UnresolvedEndpointPortError(name: Trait.Name, value: String) extends Notification

case class UnresolvedParameterError(name: Trait.Name, value: String) extends Notification
