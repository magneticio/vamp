package io.vamp.model.notification

import io.vamp.common.notification.Notification
import io.vamp.model.artifact._

case class MalformedTraitError(name: String) extends Notification

case class MissingPortValueError(breed: Breed, port: Port) extends Notification

case class MissingConstantValueError(breed: Breed, constant: Constant) extends Notification

case class RecursiveDependenciesError(breed: Breed) extends Notification

case class UnresolvedDependencyInTraitValueError(breed: Breed, reference: String) extends Notification