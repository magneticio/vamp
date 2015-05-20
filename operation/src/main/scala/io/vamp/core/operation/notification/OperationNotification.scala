package io.vamp.core.operation.notification

import io.vamp.common.akka.RequestError
import io.vamp.common.notification.{ErrorNotification, Notification}
import io.vamp.core.model.artifact._

case class InternalServerError(reason: Any) extends Notification with ErrorNotification

case class UnsupportedDeploymentRequest(request: Any) extends Notification with RequestError

case class DeploymentSynchronizationFailure(deployment: Deployment, exception: Throwable) extends Notification

case class UnresolvedVariableValueError(breed: Breed, name: String) extends Notification

case class UnresolvedEnvironmentValueError(key: String, reason: Any) extends Notification

case class UnsupportedSlaType(`type`: String) extends Notification

case class UnsupportedEscalationType(`type`: String) extends Notification

case class DeploymentServiceError(deployment: Deployment, service: DeploymentService) extends Notification

case class UnsupportedRoutingWeight(deployment: Deployment, cluster: DeploymentCluster, weight: Int) extends Notification

case class UnresolvedDependencyError(breed: Breed, dependency: Breed) extends Notification

case class NonUniqueBreedReferenceError(breed: Breed) extends Notification

case class InvalidRoutingWeight(deployment: Deployment, cluster: DeploymentCluster, service: DeploymentService, expected: Int, actual: Int) extends Notification

