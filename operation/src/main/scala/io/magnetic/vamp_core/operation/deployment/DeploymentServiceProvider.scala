package io.magnetic.vamp_core.operation.deployment

import akka.actor.{ActorContext, Actor}
import com.typesafe.scalalogging.Logger
import io.magnetic.vamp_common.akka.ExecutionContextProvider
import io.magnetic.vamp_core.model.artifact.Artifact
import io.magnetic.vamp_core.operation.ArtifactServiceProvider
import io.magnetic.vamp_core.operation.notification.OperationNotificationProvider
import io.magnetic.vamp_core.persistance.InMemoryArtifactStoreProvider
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

trait DeploymentServiceProvider extends ArtifactServiceProvider with ExecutionContextProvider with OperationNotificationProvider {
  this: Actor with ExecutionContextProvider =>

  val artifactService: ArtifactService = new DeploymentService(this.context)

  private class DeploymentService(actorContext: ActorContext) extends ArtifactService with ExecutionContextProvider with InMemoryArtifactStoreProvider {
    implicit def executionContext: ExecutionContext = actorContext.dispatcher

    private val logger = Logger(LoggerFactory.getLogger(classOf[DeploymentService]))

    def all: Future[List[Artifact]] = {
      logger.warn("All deployments - persistence only.")
      storeService.all
    }

    def create(artifact: Artifact): Future[Option[Artifact]] = {
      logger.warn("create deployment - persistence only.")
      storeService.create(artifact)
    }
    
    def read(name: String): Future[Option[Artifact]] = {
      storeService.read(name)
    }

    def update(name: String, artifact: Artifact): Future[Option[Artifact]] = {
      logger.warn("Update deployment - persistence only.")
      storeService.update(artifact.name, artifact)
    }

    def delete(name: String): Future[Option[Artifact]] = {
      logger.warn("Delete deployment - persistence only.")
      storeService.delete(name)
    }
  }

}
