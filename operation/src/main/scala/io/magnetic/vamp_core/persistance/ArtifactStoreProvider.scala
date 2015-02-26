package io.magnetic.vamp_core.persistance

import io.magnetic.vamp_common.akka.ExecutionContextProvider
import io.magnetic.vamp_core.model.artifact.Artifact
import io.magnetic.vamp_core.operation.ArtifactServiceProvider
import io.magnetic.vamp_core.operation.notification.{InconsistentResourceName, OperationNotificationProvider}

import scala.collection.mutable
import scala.concurrent.Future

trait ArtifactStoreProvider extends ArtifactServiceProvider

trait InMemoryArtifactStoreProvider extends ArtifactStoreProvider with OperationNotificationProvider {
  this: ExecutionContextProvider =>

  val artifactService: ArtifactService = new InMemoryResourceStore()

  private class InMemoryResourceStore extends ArtifactService {

    val store: mutable.Map[String, Artifact] = new mutable.HashMap()

    def all: Future[List[Artifact]] = Future {
      store.values.toList
    }
    
    def create(artifact: Artifact): Future[Option[Artifact]] = Future {
      store.put(artifact.name, artifact)
      Some(artifact)
    }

    def read(name: String): Future[Option[Artifact]] = Future {
      store.get(name)
    }

    def update(name: String, artifact: Artifact): Future[Option[Artifact]] = Future {
      if (name != artifact.name)
        error(InconsistentResourceName(name, artifact.name))

      store.put(artifact.name, artifact)
    }

    def delete(name: String): Future[Option[Artifact]] = Future {
      store.remove(name)
    }
  }

}