package io.magnetic.vamp_core.persistence.store

import io.magnetic.vamp_core.model.artifact.Artifact

import scala.concurrent.Future

trait StoreProvider {

  val store: Store

  trait Store {

    def all(`type`: Class[_ <: Artifact]): Future[List[_]]

    def create(artifact: Artifact, ignoreIfExists: Boolean = false): Future[Artifact]

    def read(name: String, `type`: Class[_ <: Artifact]): Future[Option[Artifact]]

    def update(artifact: Artifact, create: Boolean = false): Future[Artifact]

    def delete(name: String, `type`: Class[_ <: Artifact]): Future[Artifact]
  }

}
