package io.vamp.persistence.refactor.api

import io.vamp.common.{Id, Namespace}
import io.vamp.persistence.refactor.serialization.SerializationSpecifier
import spray.json.RootJsonFormat

import scala.concurrent.Future

/**
  * Created by mihai on 11/10/17.
  */
trait SimpleArtifactPersistenceDao {

  def namespace: Namespace

  def create[T](obj: T)(implicit s: SerializationSpecifier[T], serializer: RootJsonFormat[T]) : Future[Id[T]]

  def read[T](id: Id[T])(implicit s: SerializationSpecifier[T], serializer: RootJsonFormat[T]): Future[T]

  def update[T](obj: T)(implicit s: SerializationSpecifier[T], serializer: RootJsonFormat[T]): Future[Unit]

  def deleteObject[T](id: Id[T])(implicit s: SerializationSpecifier[T], serializer: RootJsonFormat[T]): Future[Unit]

  private[persistence] def afterTestCleanup: Unit
}
