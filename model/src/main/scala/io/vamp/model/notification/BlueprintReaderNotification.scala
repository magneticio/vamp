package io.vamp.model.notification

import io.vamp.common.notification.Notification
import io.vamp.model.artifact._

case class UnresolvedGatewayPortError(name: String, value: Any) extends Notification

case class UnresolvedEnvironmentVariableError(name: String, value: Any) extends Notification

case class NonUniqueBlueprintBreedReferenceError(name: String) extends Notification

case class UnresolvedBreedDependencyError(breed: Breed, dependency: (String, Breed)) extends Notification

case class UnresolvedServiceRouteError(cluster: AbstractCluster, service: String) extends Notification

case class RouteWeightError(cluster: AbstractCluster) extends Notification

case class RouteConditionStrengthError(cluster: AbstractCluster) extends Notification

case class GatewayRouteWeightError(gateway: Gateway) extends Notification

case class GatewayRouteConditionStrengthError(gateway: Gateway) extends Notification

case class UnresolvedScaleEscalationTargetCluster(cluster: AbstractCluster, target: String) extends Notification

case class MissingEnvironmentVariableError(breed: Breed, name: String) extends Notification

object NoServiceError extends Notification

case class NotificationMessageNotRestored(message: String) extends Notification

case class UndefinedStateIntentionError(name: String) extends Notification

case class UndefinedStateStepError(name: String) extends Notification

case class IllegalGatewayStickyValue(sticky: String) extends Notification

object IllegalGatewayVirtualHosts extends Notification

case class StickyPortTypeError(port: Port) extends Notification

case class ConditionPortTypeError(port: Port, condition: Condition) extends Notification

case class DuplicateGatewayPortError(port: Int) extends Notification

case class IllegalAnonymousRoutingPortMappingError(breed: Breed) extends Notification

case class UnsupportedGatewayNameError(name: String) extends Notification

case class UnsupportedRoutePathError(path: GatewayPath) extends Notification

case class InvalidGatewayPortError(port: Int) extends Notification

case class UnsupportedPathRewriteError(definition: String) extends Notification

case class UnresolvedPortReferenceError(portReference: String) extends Notification

case class NegativeFailuresNumberError(number: Int) extends Notification

case class UnsupportedProtocolError(protocol: String) extends Notification

object RouteSelectorAndRoutesDefinedError extends Notification

case class InvalidRouteSelectorError(definition: String) extends Notification

object RouteSelectorOnlyRouteError extends Notification

object RouteSelectorExternalTargetError extends Notification
