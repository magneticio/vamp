package io.magnetic.vamp_core.model.notification

import io.magnetic.vamp_common.notification.Notification
import io.magnetic.vamp_core.model.artifact.{Breed, EnvironmentVariable, Port, Trait}

case class MalformedTraitNameError(breed: Breed, name: Trait.Name) extends Notification

case class MissingPortValueError(breed: Breed, port: Port) extends Notification

case class MissingEnvironmentVariableValueError(breed: Breed, environmentVariable: EnvironmentVariable) extends Notification

case class NonUniquePortNameError(breed: Breed, port: Port) extends Notification

case class NonUniqueEnvironmentVariableNameError(breed: Breed, environmentVariable: EnvironmentVariable) extends Notification

case class UnresolvedDependencyForTraitError(breed: Breed, name: Trait.Name) extends Notification
