package io.vamp.operation.workflow

import akka.actor.ActorSystem
import io.vamp.model.artifact.Artifact
import io.vamp.model.workflow.ScheduledWorkflow
import io.vamp.operation.controller.ArtifactApiController

import scala.concurrent.ExecutionContext

class ArtifactApiContext(group: String)(implicit scheduledWorkflow: ScheduledWorkflow, ec: ExecutionContext, actorSystem: ActorSystem) extends ApiContext with ArtifactApiController {

  def all() = serialize {
    allPages[Artifact](allArtifacts(group, expandReferences = true, onlyReferences = false))
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
