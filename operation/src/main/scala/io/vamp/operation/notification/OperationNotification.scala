package io.vamp.operation.notification

import io.vamp.common.akka.RequestError
import io.vamp.common.notification.{ ErrorNotification, Notification }
import io.vamp.model.artifact._

case class InternalServerError(reason: Any) extends Notification with ErrorNotification

case class UnsupportedDeploymentRequest(request: Any) extends Notification with RequestError

case class UnsupportedGatewayRequest(request: Any) extends Notification with RequestError

case class DeploymentSynchronizationFailure(deployment: Deployment, exception: Throwable) extends Notification

case class UnresolvedVariableValueError(breed: Breed, name: String) extends Notification

case class UnresolvedEnvironmentValueError(key: String, reason: Any) extends Notification

case class UnsupportedSlaType(`type`: String) extends Notification

case class UnsupportedEscalationType(`type`: String) extends Notification

case class DeploymentServiceError(deployment: Deployment, service: DeploymentService) extends Notification

case class UnsupportedRouteWeight(deployment: Deployment, cluster: DeploymentCluster, weight: Int) extends Notification

case class NonUniqueBreedReferenceError(breed: Breed) extends Notification

case class InvalidRouteWeight(deployment: Deployment, cluster: DeploymentCluster, weight: Int) extends Notification

case class UnavailableGatewayPortError(port: Port, gateway: Gateway) extends Notification

case class WorkflowSchedulingError(reason: Any) extends Notification with ErrorNotification

case class WorkflowExecutionError(reason: Any) extends Notification with ErrorNotification

case class UnexpectedArtifact(artifact: String) extends Notification

case class InvalidTimeTriggerError(pattern: String) extends Notification

case class MissingRequiredVariableError(required: String) extends Notification

case class NoAvailablePortError(begin: Int, end: Int) extends Notification

case class InternalGatewayCreateError(name: String) extends Notification

case class InternalGatewayUpdateError(name: String) extends Notification

case class InternalGatewayRemoveError(name: String) extends Notification
