package io.magnetic.vamp_core.operation.notification

import io.magnetic.vamp_common.akka.RequestError
import io.magnetic.vamp_common.notification.{ErrorNotification, Notification}
import io.magnetic.vamp_core.model.artifact.{Trait, Breed, Deployment}

case class InternalServerError(reason: Any) extends Notification with ErrorNotification

case class UnsupportedDeploymentRequest(request: Any) extends Notification with RequestError

case class NonUniqueBreedReferenceError(breed: Breed) extends Notification

case class UnresolvedDependencyError(breed: Breed, dependency: Breed) extends Notification

case class DeploymentSynchronizationFailure(deployment: Deployment, exception: Throwable) extends Notification

case class UnresolvedVariableValueError(breed: Breed, name: Trait.Name) extends Notification

case class UnresolvedEnvironmentValueError(key: String, reason: Any) extends Notification

case class UnsupportedSlaType(`type`: String) extends Notification

case class UnsupportedEscalationType(`type`: String) extends Notification
