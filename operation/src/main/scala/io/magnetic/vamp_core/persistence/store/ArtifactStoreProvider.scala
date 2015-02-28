package io.magnetic.vamp_core.persistence.store

import io.magnetic.vamp_core.model.artifact.Artifact

import scala.concurrent.Future

trait ArtifactStoreProvider {

  val store: ArtifactStore

  trait ArtifactStore {

    def all(`type`: Class[Artifact]): Future[List[Artifact]]

    def create(artifact: Artifact): Future[Artifact]

    def read(name: String, `type`: Class[Artifact]): Future[Option[Artifact]]

    def update(artifact: Artifact): Future[Artifact]

    def delete(name: String, `type`: Class[Artifact]): Future[Artifact]
  }

}
