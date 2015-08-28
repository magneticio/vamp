package io.vamp.core.operation.workflow

import akka.actor.ActorRefFactory
import io.vamp.core.model.workflow.ScheduledWorkflow
import io.vamp.core.operation.controller.ArtifactApiController

import scala.concurrent.ExecutionContext

class ArtifactApiContext(group: String)(implicit scheduledWorkflow: ScheduledWorkflow, ec: ExecutionContext, arf: ActorRefFactory) extends ApiContext with ArtifactApiController {

  def all() = serialize {
    allPages(allArtifacts(group, expandReferences = true, onlyReferences = false))
  }

  def get(name: String) = serialize {
    readArtifact(group, name, expandReferences = true, onlyReferences = false)
  }

  def create(source: Any) = serialize {
    createArtifact(group, load(source), validateOnly = false)
  }

  def update(source: Any) = serialize {
    updateArtifact(group, nameOf(source), load(source), validateOnly = false)
  }

  def delete(source: Any) = serialize {
    deleteArtifact(group, nameOf(source), load(source), validateOnly = false)
  }
}
