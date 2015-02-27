package io.magnetic.vamp_core.operation.deployment

import io.magnetic.vamp_core.model.artifact.Blueprint
import io.magnetic.vamp_core.model.deployment.Deployment

object BlueprintMerger {

  def merge(blueprint: Blueprint, deployment: Option[Deployment]): Deployment = {
    Deployment(blueprint.name: String, List(), Map(), Map())
  }
}
