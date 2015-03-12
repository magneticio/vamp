package io.magnetic.vamp_core.model.notification

import io.magnetic.vamp_common.notification.Notification
import io.magnetic.vamp_core.model.artifact.{Breed, Trait}

case class UnresolvedEndpointPortError(name: Trait.Name, value: Any) extends Notification

case class UnresolvedParameterError(name: Trait.Name, value: Any) extends Notification

case class NonUniqueBlueprintBreedReferenceError(name: String) extends Notification

case class UnresolvedBreedDependencyError(breed: Breed, dependency: (String, Breed)) extends Notification
