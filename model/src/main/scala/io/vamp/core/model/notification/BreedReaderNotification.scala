package io.vamp.core.model.notification

import io.vamp.common.notification.Notification
import io.vamp.core.model.artifact.{Port, EnvironmentVariable, Trait, Breed}

case class MalformedTraitNameError(breed: Breed, name: Trait.Name) extends Notification

case class MissingPortValueError(breed: Breed, port: Port) extends Notification

case class MissingEnvironmentVariableValueError(breed: Breed, environmentVariable: EnvironmentVariable) extends Notification

case class NonUniquePortNameError(breed: Breed, port: Port) extends Notification

case class NonUniqueEnvironmentVariableNameError(breed: Breed, environmentVariable: EnvironmentVariable) extends Notification

case class UnresolvedDependencyForTraitError(breed: Breed, name: Trait.Name) extends Notification

case class RecursiveDependenciesError(breed: Breed) extends Notification
