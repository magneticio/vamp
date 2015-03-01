package io.magnetic.vamp_core.persistence.store

import scala.concurrent.Future

trait StoreProvider {

  val store: Store

  trait Store {

    def all(`type`: Class[_]): Future[List[_]]

    def create(any: AnyRef, ignoreIfExists: Boolean = false): Future[AnyRef]

    def read(name: String, `type`: Class[_]): Future[Option[AnyRef]]

    def update(any: AnyRef): Future[AnyRef]

    def delete(name: String, `type`: Class[_]): Future[AnyRef]
  }

}
