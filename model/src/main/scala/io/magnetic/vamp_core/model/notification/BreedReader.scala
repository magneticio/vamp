package io.magnetic.vamp_core.model.notification

import io.magnetic.vamp_common.notification.Notification
import io.magnetic.vamp_core.model.{Breed, EnvironmentVariable, Port}

case class MissingPortValueError(breed: Breed, port: Port) extends Notification

case class MissingEnvironmentVariableValueError(breed: Breed, environmentVariable: EnvironmentVariable) extends Notification

case class NonUniquePortNameError(breed: Breed, port: Port) extends Notification

case class NonUniqueEnvironmentVariableNameError(breed: Breed, environmentVariable: EnvironmentVariable) extends Notification


