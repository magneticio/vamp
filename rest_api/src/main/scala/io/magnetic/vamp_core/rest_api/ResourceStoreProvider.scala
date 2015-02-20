package io.magnetic.vamp_core.rest_api

import io.magnetic.vamp_core.model.Artifact
import io.magnetic.vamp_core.rest_api.util.ExecutionContextProvider

import scala.collection.mutable
import scala.concurrent.Future

trait ResourceStoreProvider[A <: Artifact] {

  val resourceStore: ResourceStore

  trait ResourceStore {

    def all: Future[List[A]]

    def find(name: String): Future[Option[A]]

    def create(resource: A): Future[Option[A]]

    def update(resource: A): Future[Option[A]]

    def delete(name: String): Future[Option[A]]
  }

}

trait InMemoryResourceStoreProvider[A <: Artifact] extends ResourceStoreProvider[A] {
  this: ExecutionContextProvider =>
  
  val resourceStore: ResourceStore = new InMemoryResourceStore()

  private class InMemoryResourceStore extends ResourceStore {

    val store: mutable.Map[String, A] = new mutable.HashMap()

    def all: Future[List[A]] = Future {
      store.values.toList
    }

    def find(name: String): Future[Option[A]] = Future {
      store.get(name)
    }

    def create(resource: A): Future[Option[A]] = Future {
      store.put(resource.name, resource)
      Some(resource)
    }

    def update(resource: A): Future[Option[A]] = Future {
      store.put(resource.name, resource)
    }

    def delete(name: String): Future[Option[A]] = Future {
      store.remove(name)
    }
  }

}