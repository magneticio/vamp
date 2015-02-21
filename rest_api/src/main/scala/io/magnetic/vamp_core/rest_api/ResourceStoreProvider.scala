package io.magnetic.vamp_core.rest_api

import io.magnetic.vamp_core.model.Artifact
import io.magnetic.vamp_core.rest_api.notification.{InconsistentResourceName, RestApiNotificationProvider}
import io.magnetic.vamp_core.rest_api.util.ExecutionContextProvider

import scala.collection.mutable
import scala.concurrent.Future

trait ResourceStoreProvider {

  val resourceStore: ResourceStore

  trait ResourceStore {

    def all: Future[List[Artifact]]

    def find(name: String): Future[Option[Artifact]]

    def create(resource: Artifact): Future[Option[Artifact]]

    def update(name: String, resource: Artifact): Future[Option[Artifact]]

    def delete(name: String): Future[Option[Artifact]]
  }

}

trait InMemoryResourceStoreProvider extends ResourceStoreProvider with RestApiNotificationProvider {
  this: ExecutionContextProvider =>

  val resourceStore: ResourceStore = new InMemoryResourceStore()

  private class InMemoryResourceStore extends ResourceStore {

    val store: mutable.Map[String, Artifact] = new mutable.HashMap()

    def all: Future[List[Artifact]] = Future {
      store.values.toList
    }

    def find(name: String): Future[Option[Artifact]] = Future {
      store.get(name)
    }

    def create(resource: Artifact): Future[Option[Artifact]] = Future {
      store.put(resource.name, resource)
      Some(resource)
    }

    def update(name: String, resource: Artifact): Future[Option[Artifact]] = Future {
      if (name != resource.name)
        error(InconsistentResourceName(name, resource.name))

      store.put(resource.name, resource)
    }

    def delete(name: String): Future[Option[Artifact]] = Future {
      store.remove(name)
    }
  }

}