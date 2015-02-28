package io.magnetic.vamp_core.persistence.store

import io.magnetic.vamp_common.akka.ExecutionContextProvider
import io.magnetic.vamp_core.model.artifact.Artifact
import io.magnetic.vamp_core.operation.notification.OperationNotificationProvider
import io.magnetic.vamp_core.persistence.notification.ArtifactNotFound

import scala.collection.mutable
import scala.concurrent.Future


trait InMemoryStoreProvider extends StoreProvider with OperationNotificationProvider {
  this: ExecutionContextProvider =>

  val store: ArtifactStore = new InMemoryArtifactStore()

  private class InMemoryArtifactStore extends ArtifactStore {

    val store: mutable.Map[Class[_ <: Artifact], mutable.Map[String, Artifact]] = new mutable.HashMap()

    def all(`type`: Class[Artifact]): Future[List[Artifact]] = Future {
      store.get(`type`) match {
        case None => Nil
        case Some(map) => map.values.toList
      }
    }

    def create(artifact: Artifact): Future[Artifact] = Future {
      store.get(artifact.getClass) match {
        case None =>
          val map = new mutable.HashMap[String, Artifact]()
          map.put(artifact.name, artifact)
          store.put(artifact.getClass, map)
        case Some(map) => map.put(artifact.name, artifact)
      }
      artifact
    }

    def read(name: String, `type`: Class[Artifact]): Future[Option[Artifact]] = Future {
      store.get(`type`) match {
        case None => None
        case Some(map) => map.get(name)
      }
    }

    def update(artifact: Artifact): Future[Artifact] = Future {
      store.get(artifact.getClass) match {
        case None => error(ArtifactNotFound(artifact.name, artifact.getClass))
        case Some(map) =>
          if (map.get(artifact.name).isEmpty)
            error(ArtifactNotFound(artifact.name, artifact.getClass))
          else
            map.put(artifact.name, artifact)
      }
      artifact
    }

    def delete(name: String, `type`: Class[Artifact]): Future[Artifact] = Future {
      store.get(`type`) match {
        case None => error(ArtifactNotFound(name, `type`))
        case Some(map) =>
          if (map.get(name).isEmpty)
            error(ArtifactNotFound(name, `type`))
          else
            map.remove(name).get
      }
    }
  }

}
