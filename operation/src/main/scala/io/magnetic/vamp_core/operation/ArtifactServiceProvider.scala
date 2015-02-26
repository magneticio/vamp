package io.magnetic.vamp_core.operation

import io.magnetic.vamp_core.model.artifact.Artifact

import scala.concurrent.Future

trait ArtifactServiceProvider {

  val artifactService: ArtifactService

  trait ArtifactService {

    def all: Future[List[Artifact]]

    def read(name: String): Future[Option[Artifact]]

    def create(artifact: Artifact): Future[Option[Artifact]]

    def update(name: String, artifact: Artifact): Future[Option[Artifact]]

    def delete(name: String): Future[Option[Artifact]]
  }

}
