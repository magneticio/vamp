package io.vamp.core.operation.workflow

import akka.actor.ActorRefFactory
import io.vamp.core.model.workflow.ScheduledWorkflow
import io.vamp.core.operation.controller.DeploymentApiController

import scala.concurrent.ExecutionContext

class DeploymentApiContext(implicit scheduledWorkflow: ScheduledWorkflow, ec: ExecutionContext, arf: ActorRefFactory) extends ApiContext with DeploymentApiController {

  def all() = serialize {
    allPages(deployments(asBlueprint = false, expandReferences = true, onlyReferences = false))
  }

  def get(name: String) = serialize {
    deployment(name, asBlueprint = false, expandReferences = true, onlyReferences = false)
  }

  def create(source: Any) = serialize {
    createDeployment(load(source), validateOnly = false)
  }

  def update(name: String, source: Any) = serialize {
    updateDeployment(name, load(source), validateOnly = false)
  }

  def delete(name: String, source: Any) = serialize {
    deleteDeployment(name, load(source), validateOnly = false)
  }
}
