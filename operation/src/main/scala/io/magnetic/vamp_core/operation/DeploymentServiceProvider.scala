package io.magnetic.vamp_core.operation

import com.typesafe.scalalogging.Logger
import io.magnetic.vamp_common.akka.ExecutionContextProvider
import io.magnetic.vamp_core.model.artifact.Artifact
import io.magnetic.vamp_core.operation.notification.OperationNotificationProvider
import io.magnetic.vamp_core.persistance.InMemoryArtifactStoreProvider
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

trait DeploymentServiceProvider extends ArtifactServiceProvider with ExecutionContextProvider with OperationNotificationProvider {
  this: ExecutionContextProvider =>

  val artifactService: ArtifactService = new DeploymentService(executionContext)

  private class DeploymentService(ec: ExecutionContext) extends ArtifactService with ExecutionContextProvider with InMemoryArtifactStoreProvider {
    implicit def executionContext: ExecutionContext = ec

    private val logger = Logger(LoggerFactory.getLogger(classOf[DeploymentService]))

    def all: Future[List[Artifact]] = {
      logger.warn("All deployments - persistence only.")
      artifactService.all
    }

    def create(artifact: Artifact): Future[Option[Artifact]] = {
      logger.warn("create deployment - persistence only.")
      artifactService.create(artifact)
    }
    
    def read(name: String): Future[Option[Artifact]] = {
      artifactService.read(name)
    }

    def update(name: String, artifact: Artifact): Future[Option[Artifact]] = {
      logger.warn("Update deployment - persistence only.")
      artifactService.update(name, artifact)
    }

    def delete(name: String): Future[Option[Artifact]] = {
      logger.warn("Delete deployment - persistence only.")
      artifactService.delete(name)
    }
  }

}
