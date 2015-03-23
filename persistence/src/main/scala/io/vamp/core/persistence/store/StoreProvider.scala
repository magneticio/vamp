package io.vamp.core.persistence.store

import io.vamp.core.model.artifact.Artifact

trait StoreProvider {

  val store: Store

  trait Store {

    def all(`type`: Class[_ <: Artifact]): List[Artifact]

    def create(artifact: Artifact, ignoreIfExists: Boolean = false): Artifact

    def read(name: String, `type`: Class[_ <: Artifact]): Option[Artifact]

    def update(artifact: Artifact, create: Boolean = false): Artifact

    def delete(name: String, `type`: Class[_ <: Artifact]): Artifact
  }

}
