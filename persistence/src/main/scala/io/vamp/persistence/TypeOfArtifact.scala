package io.vamp.persistence

import io.vamp.common.Namespace
import io.vamp.common.notification.NotificationProvider
import io.vamp.model.artifact._
import io.vamp.persistence.notification.UnsupportedPersistenceRequest

trait TypeOfArtifact {
  this: NotificationProvider ⇒

  protected def type2string(`type`: Class[_]): String = `type` match {
    // gateway persistence
    case t if classOf[RouteTargets].isAssignableFrom(t) ⇒ "route-targets"
    case t if classOf[GatewayPort].isAssignableFrom(t) ⇒ "gateway-ports"
    case t if classOf[GatewayServiceAddress].isAssignableFrom(t) ⇒ "gateway-services"
    case t if classOf[GatewayDeploymentStatus].isAssignableFrom(t) ⇒ "gateway-deployment-statuses"
    case t if classOf[InternalGateway].isAssignableFrom(t) ⇒ "internal-gateway"
    // deployment persistence
    case t if classOf[DeploymentServiceStatus].isAssignableFrom(t) ⇒ "deployment-service-statuses"
    case t if classOf[DeploymentServiceScale].isAssignableFrom(t) ⇒ "deployment-service-scales"
    case t if classOf[DeploymentServiceInstances].isAssignableFrom(t) ⇒ "deployment-service-instances"
    case t if classOf[DeploymentServiceEnvironmentVariables].isAssignableFrom(t) ⇒ "deployment-service-environment-variables"
    case t if classOf[DeploymentServiceHealth].isAssignableFrom(t) ⇒ "deployment-service-health"
    // workflow persistence
    case t if classOf[WorkflowBreed].isAssignableFrom(t) ⇒ "workflow-breed"
    case t if classOf[WorkflowStatus].isAssignableFrom(t) ⇒ "workflow-status"
    case t if classOf[WorkflowScale].isAssignableFrom(t) ⇒ "workflow-scale"
    case t if classOf[WorkflowNetwork].isAssignableFrom(t) ⇒ "workflow-network"
    case t if classOf[WorkflowArguments].isAssignableFrom(t) ⇒ "workflow-arguments"
    case t if classOf[WorkflowEnvironmentVariables].isAssignableFrom(t) ⇒ "workflow-environment-variables"
    case t if classOf[WorkflowInstances].isAssignableFrom(t) ⇒ "workflow-instances"
    case t if classOf[WorkflowHealth].isAssignableFrom(t) ⇒ "workflow-health"
    //
    case t if classOf[Gateway].isAssignableFrom(t) ⇒ "gateways"
    case t if classOf[Deployment].isAssignableFrom(t) ⇒ "deployments"
    case t if classOf[Breed].isAssignableFrom(t) ⇒ "breeds"
    case t if classOf[Blueprint].isAssignableFrom(t) ⇒ "blueprints"
    case t if classOf[Sla].isAssignableFrom(t) ⇒ "slas"
    case t if classOf[Scale].isAssignableFrom(t) ⇒ "scales"
    case t if classOf[Escalation].isAssignableFrom(t) ⇒ "escalations"
    case t if classOf[Route].isAssignableFrom(t) ⇒ "routes"
    case t if classOf[Condition].isAssignableFrom(t) ⇒ "conditions"
    case t if classOf[Rewrite].isAssignableFrom(t) ⇒ "rewrites"
    case t if classOf[Workflow].isAssignableFrom(t) ⇒ "workflows"
    case t if classOf[Namespace].isAssignableFrom(t) ⇒ "namespaces"
    case t if classOf[Template].isAssignableFrom(t) ⇒ "templates"
    case _ ⇒ throwException(UnsupportedPersistenceRequest(`type`))
  }
}
