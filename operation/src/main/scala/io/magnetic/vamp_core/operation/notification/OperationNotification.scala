package io.magnetic.vamp_core.operation.notification

import io.magnetic.vamp_common.akka.RequestError
import io.magnetic.vamp_common.notification.Notification
import io.magnetic.vamp_core.model.artifact.{Breed, Deployment}

case class InternalServerError(any: Any) extends Notification

case class UnsupportedDeploymentRequest(request: Any) extends Notification with RequestError

case class NonUniqueBreedReferenceError(breed: Breed) extends Notification

case class UnresolvedDependencyError(breed: Breed, dependency: Breed) extends Notification

case class DeploymentSynchronizationFailure(deployment: Deployment, exception: Throwable) extends Notification
