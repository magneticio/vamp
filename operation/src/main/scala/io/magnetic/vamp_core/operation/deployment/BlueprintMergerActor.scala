package io.magnetic.vamp_core.operation.deployment

import akka.actor.{Actor, ActorLogging}
import io.magnetic.vamp_core.model.artifact.Blueprint
import io.magnetic.vamp_core.model.deployment.Deployment
import io.magnetic.vamp_core.operation.notification.OperationNotificationProvider
import io.magnetic.vamp_core.operation.{OperationActor, OperationRequest}

case class BlueprintMergeRequest(blueprint: Blueprint, deployment: Option[Deployment]) extends OperationRequest

class BlueprintMergerActor extends Actor with OperationActor with ActorLogging with OperationNotificationProvider {

  def reply(request: Any) = request match {
    case BlueprintMergeRequest(blueprint, Some(deployment)) => merge(blueprint, deployment)
    case BlueprintMergeRequest(blueprint, None) => merge(blueprint, Deployment(blueprint.name: String, List(), Map(), Map()))
  }

  def merge(blueprint: Blueprint, deployment: Deployment): Deployment = {
    Deployment(blueprint.name: String, List(), Map(), Map())
  }
}
