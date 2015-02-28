package io.magnetic.vamp_core.operation.deployment

import akka.actor.Actor
import io.magnetic.vamp_common.akka.ExecutionContextProvider
import io.magnetic.vamp_core.operation.ArtifactServiceProvider
import io.magnetic.vamp_core.operation.notification.OperationNotificationProvider

trait DeploymentServiceProvider extends ArtifactServiceProvider with ExecutionContextProvider with OperationNotificationProvider {
  this: Actor with ExecutionContextProvider =>

  //  val artifactService: ArtifactService = new DeploymentService(this.context)
  //
  //  private class DeploymentService(actorContext: ActorContext) extends ArtifactService with ExecutionContextProvider {
  //    implicit def executionContext: ExecutionContext = actorContext.dispatcher
  //
  //    private val logger = Logger(LoggerFactory.getLogger(classOf[DeploymentService]))
  //
  //    def all: Future[List[Artifact]] = {
  //      logger.warn("All deployments.")
  //    }
  //
  //    def create(artifact: Artifact): Future[Option[Artifact]] = {
  //      logger.warn("create deployment.")
  //    }
  //
  //    def read(name: String): Future[Option[Artifact]] = {
  //    }
  //
  //    def update(name: String, artifact: Artifact): Future[Option[Artifact]] = {
  //      logger.warn("Update deployment.")
  //    }
  //
  //    def delete(name: String): Future[Option[Artifact]] = {
  //      logger.warn("Delete deployment.")
  //    }
  //  }

}
